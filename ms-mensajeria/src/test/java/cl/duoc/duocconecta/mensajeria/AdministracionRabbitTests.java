package cl.duoc.duocconecta.mensajeria;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.mockito.Mockito;

/** El administrador de RabbitMQ contra un AmqpAdmin falso: validación, protección y permisos. */
@SpringBootTest(properties = "duocconecta.mensajeria.rabbit.habilitada=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionRabbitTests {

    private static final String RAIZ = "/api/v1/mensajeria/rabbit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AmqpAdmin admin;

    @MockitoBean
    private RabbitTemplate plantilla;

    private static RequestPostProcessor token(String... roles) {
        return jwt().jwt(t -> t.claim("oid", "oid-x").claim("email", "admin@duocuc.cl"))
                .authorities(Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r)).toList());
    }

    private static RequestPostProcessor admin() {
        return token("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }

    private org.springframework.test.web.servlet.ResultActions postear(String ruta, String cuerpo) throws Exception {
        return mockMvc.perform(post(RAIZ + ruta).with(admin()).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    @Test
    @DisplayName("Sin rol ADMINISTRADOR: 403, y sin token: 401")
    void soloAdministrador() throws Exception {
        mockMvc.perform(get(RAIZ + "/colas/duocconecta.auditoria")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(RAIZ + "/colas/duocconecta.auditoria").with(token("ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Crear una cola con DLQ: declara la DLQ, el binding al DLX y la cola con su límite de entregas")
    void crearColaConDescarte() throws Exception {
        when(admin.getQueueInfo("duocconecta.prueba"))
                .thenReturn(null)
                .thenReturn(new QueueInformation("duocconecta.prueba", 0, 0));

        postear("/colas", "{\"nombre\":\"duocconecta.prueba\",\"maxEntregas\":4}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("duocconecta.prueba"));

        verify(admin).declareQueue(argThat((Queue q) -> q.getName().equals("duocconecta.prueba.descartes")));
        verify(admin).declareBinding(argThat((Binding b) -> b.getDestination().equals("duocconecta.prueba.descartes")
                && b.getExchange().equals("duocconecta.dlx")));
        verify(admin).declareQueue(argThat((Queue q) -> q.getName().equals("duocconecta.prueba")
                && "duocconecta.dlx".equals(q.getArguments().get("x-dead-letter-exchange"))
                && Integer.valueOf(3).equals(q.getArguments().get("x-delivery-limit"))
                && "quorum".equals(q.getArguments().get("x-queue-type"))));
    }

    @Test
    @DisplayName("Nombres y parámetros inválidos: 400 con un mensaje claro y sin tocar RabbitMQ")
    void validacion() throws Exception {
        postear("/colas", "{\"nombre\":\"\"}").andExpect(status().isBadRequest());
        postear("/colas", "{\"nombre\":\"cola-ajena\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre", containsString("duocconecta.")));
        postear("/colas", "{\"nombre\":\"duocconecta.prueba\",\"maxEntregas\":0}").andExpect(status().isBadRequest());
        postear("/colas", "{\"nombre\":\"duocconecta.prueba\",\"conDescarte\":false,\"maxEntregas\":3}")
                .andExpect(status().isBadRequest());
        postear("/exchanges", "{\"nombre\":\"duocconecta.x\",\"tipo\":\"HEADERS\"}").andExpect(status().isBadRequest());
        postear("/exchanges", "{\"nombre\":\"duocconecta.x\"}").andExpect(status().isBadRequest());
        postear("/bindings", "{\"exchange\":\"duocconecta.x\",\"cola\":\"duocconecta.y\",\"routingKey\":\"Proyecto Con Espacios\"}")
                .andExpect(status().isBadRequest());
        verify(admin, never()).declareQueue(any(Queue.class));
        verify(admin, never()).declareExchange(any(Exchange.class));
    }

    @Test
    @DisplayName("La topología base no se elimina: 409; una cola ajena: 400")
    void protegidaYAjena() throws Exception {
        mockMvc.perform(delete(RAIZ + "/colas/duocconecta.auditoria").with(admin())).andExpect(status().isConflict());
        mockMvc.perform(delete(RAIZ + "/exchanges/duocconecta.eventos").with(admin())).andExpect(status().isConflict());
        mockMvc.perform(delete(RAIZ + "/colas/cola-de-otro-equipo").with(admin())).andExpect(status().isBadRequest());
        verify(admin, never()).deleteQueue(anyString());
        verify(admin, never()).deleteExchange(anyString());
    }

    @Test
    @DisplayName("Crear un exchange topic y un binding con patrón")
    void exchangeYBinding() throws Exception {
        when(admin.getQueueInfo("duocconecta.prueba")).thenReturn(new QueueInformation("duocconecta.prueba", 0, 0));
        postear("/exchanges", "{\"nombre\":\"duocconecta.prueba.topic\",\"tipo\":\"TOPIC\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("TOPIC"));
        postear("/bindings", "{\"exchange\":\"duocconecta.prueba.topic\",\"cola\":\"duocconecta.prueba\",\"routingKey\":\"proyecto.#\"}")
                .andExpect(status().isCreated());
        verify(admin).declareExchange(argThat((Exchange e) -> e.getType().equals("topic")));
        verify(admin).declareBinding(argThat((Binding b) -> b.getRoutingKey().equals("proyecto.#")));
    }

    @Test
    @DisplayName("Listar: las colas de la topología base que existen, con sus contadores")
    void listar() throws Exception {
        when(admin.getQueueInfo("duocconecta.auditoria")).thenReturn(new QueueInformation("duocconecta.auditoria", 2, 1));
        when(admin.getQueueInfo("duocconecta.auditoria.descartes"))
                .thenReturn(new QueueInformation("duocconecta.auditoria.descartes", 1, 0));
        mockMvc.perform(get(RAIZ + "/colas").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("duocconecta.auditoria"))
                .andExpect(jsonPath("$[0].mensajes").value(2))
                .andExpect(jsonPath("$[0].dlq").value("duocconecta.auditoria.descartes"))
                .andExpect(jsonPath("$[0].protegida").value(true));
    }

    @Test
    @DisplayName("Purga: borra y cuenta; una cola ajena es 400 y una que no existe, 404")
    void purga() throws Exception {
        when(admin.getQueueInfo("duocconecta.auditoria.descartes"))
                .thenReturn(new QueueInformation("duocconecta.auditoria.descartes", 4, 0));
        when(admin.purgeQueue("duocconecta.auditoria.descartes")).thenReturn(4);
        mockMvc.perform(post(RAIZ + "/colas/duocconecta.auditoria.descartes/purga").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.borrados").value(4));
        mockMvc.perform(post(RAIZ + "/colas/cola-ajena/purga").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(post(RAIZ + "/colas/duocconecta.no-existe/purga").with(admin())).andExpect(status().isNotFound());
        verify(admin, never()).purgeQueue("cola-ajena");
    }

    @Test
    @DisplayName("Redrive: cada mensaje se publica en su cola y recién entonces se confirma en la DLQ")
    void redrive() throws Exception {
        String dlq = "duocconecta.notificaciones.colaboraciones.descartes";
        String destino = "duocconecta.notificaciones.colaboraciones";
        when(admin.getQueueInfo(dlq)).thenReturn(new QueueInformation(dlq, 2, 0));
        when(admin.getQueueInfo(destino)).thenReturn(new QueueInformation(destino, 0, 1));
        Channel canal = Mockito.mock(Channel.class);
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder().messageId("e-1").build();
        when(canal.basicGet(dlq, false))
                .thenReturn(new GetResponse(new Envelope(7, false, "duocconecta.dlx", dlq), props, "{}".getBytes(), 1))
                .thenReturn(new GetResponse(new Envelope(8, false, "duocconecta.dlx", dlq), props, "{}".getBytes(), 0))
                .thenReturn(null);
        when(plantilla.execute(any())).thenAnswer(inv -> ((ChannelCallback<?>) inv.getArgument(0)).doInRabbit(canal));

        mockMvc.perform(post(RAIZ + "/colas/" + dlq + "/redrive").with(admin()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.colaDestino").value(destino))
                .andExpect(jsonPath("$.movidos").value(2));

        var orden = Mockito.inOrder(canal);
        orden.verify(canal).basicPublish(eq(""), eq(destino), eq(props), any(byte[].class));
        orden.verify(canal).basicAck(7, false);
        verify(canal).basicAck(8, false);
    }

    @Test
    @DisplayName("Redrive desde algo que no es DLQ: 400 sin tocar RabbitMQ")
    void redriveSoloDesdeDlq() throws Exception {
        mockMvc.perform(post(RAIZ + "/colas/duocconecta.auditoria/redrive").with(admin())).andExpect(status().isBadRequest());
        verify(plantilla, never()).execute(any());
    }
}
