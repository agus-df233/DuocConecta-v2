package cl.duoc.duocconecta.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

/**
 * El administrador de colas contra un SQS falso: los métodos del SDK con lambda son
 * {@code default} y delegan en los que reciben el request, que son los que se simulan aquí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionDeColasTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private SqsClient sqs;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private SnsClient sns;

    /** Nombre de cola → atributos. Es el estado del SQS falso. */
    private final Map<String, Map<QueueAttributeName, String>> colas = new HashMap<>();
    private final List<String> creadasEnOrden = new ArrayList<>();

    @BeforeEach
    void sqsFalso() {
        colas.clear();
        creadasEnOrden.clear();
        doAnswer(inv -> {
            String nombre = inv.<GetQueueUrlRequest>getArgument(0).queueName();
            if (!colas.containsKey(nombre)) {
                throw QueueDoesNotExistException.builder().message("no existe").build();
            }
            return GetQueueUrlResponse.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/1/" + nombre).build();
        }).when(sqs).getQueueUrl(any(GetQueueUrlRequest.class));
        doAnswer(inv -> {
            CreateQueueRequest pedido = inv.getArgument(0);
            Map<QueueAttributeName, String> atributos = new HashMap<>(pedido.attributes());
            atributos.put(QueueAttributeName.QUEUE_ARN, "arn:aws:sqs:us-east-1:1:" + pedido.queueName());
            colas.put(pedido.queueName(), atributos);
            creadasEnOrden.add(pedido.queueName());
            return CreateQueueResponse.builder().queueUrl("https://sqs.us-east-1.amazonaws.com/1/" + pedido.queueName()).build();
        }).when(sqs).createQueue(any(CreateQueueRequest.class));
        doAnswer(inv -> {
            String url = inv.<GetQueueAttributesRequest>getArgument(0).queueUrl();
            return GetQueueAttributesResponse.builder().attributes(colas.get(url.substring(url.lastIndexOf('/') + 1))).build();
        }).when(sqs).getQueueAttributes(any(GetQueueAttributesRequest.class));
    }

    @Test
    @DisplayName("Sin token 401; sin rol ADMINISTRADOR 403")
    void soloAdministrador() throws Exception {
        mockMvc.perform(get("/api/v1/mensajeria/colas")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/mensajeria/colas").with(token("ROLE_ESTUDIANTE"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/mensajeria/topicos").with(token("ROLE_PROFESOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"duocconecta-x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Nombres vacíos, con puntos o sin el prefijo: 400 con un mensaje claro")
    void nombresInvalidos() throws Exception {
        for (String nombre : new String[]{"", "duocconecta.prueba", "otra-cola", "duocconecta-", "Duocconecta-Prueba"}) {
            crearCola("{\"nombre\":\"" + nombre + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errores.nombre").exists());
        }
        crearCola("{\"nombre\":\"duocconecta-" + "a".repeat(60) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.*", hasItem(containsString("69"))));
    }

    @Test
    @DisplayName("Rangos de SQS y reglas cruzadas: 400")
    void parametrosInvalidos() throws Exception {
        crearCola("{\"nombre\":\"duocconecta-prueba\",\"retencionSegundos\":30}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.*", hasItem(containsString("60 segundos"))));
        crearCola("{\"nombre\":\"duocconecta-prueba\",\"esperaSegundos\":25}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.*", hasItem(containsString("20 segundos"))));
        crearCola("{\"nombre\":\"duocconecta-prueba\",\"retencionSegundos\":86400,\"retencionDescarteSegundos\":3600}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.*", hasItem(containsString("DLQ tiene que retener más"))));
        crearCola("{\"nombre\":\"duocconecta-prueba\",\"conDescarte\":false,\"maxRecepciones\":5}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.*", hasItem(containsString("solo aplica"))));
        mockMvc.perform(post("/api/v1/mensajeria/suscripciones").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topico\":\"duocconecta-eventos\",\"cola\":\"duocconecta-prueba\",\"filtroPrefijo\":\"Proyecto\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.*", hasItem(containsString("terminado en punto"))));
        verify(sqs, never()).createQueue(any(CreateQueueRequest.class));
    }

    @Test
    @DisplayName("Crear: primero la DLQ, después la cola con su redrive; 201 con Location")
    void crearColaConDescarte() throws Exception {
        crearCola("{\"nombre\":\"duocconecta-prueba\",\"maxRecepciones\":5}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/mensajeria/colas/duocconecta-prueba"))
                .andExpect(jsonPath("$.colaDescarte").value("duocconecta-prueba-descartes"))
                .andExpect(jsonPath("$.maxRecepciones").value(5))
                .andExpect(jsonPath("$.retencionSegundos").value(345600))
                .andExpect(jsonPath("$.protegida").value(false));

        assertThat(creadasEnOrden).containsExactly("duocconecta-prueba-descartes", "duocconecta-prueba");
        assertThat(colas.get("duocconecta-prueba-descartes").get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("1209600");
        assertThat(colas.get("duocconecta-prueba").get(QueueAttributeName.SQS_MANAGED_SSE_ENABLED)).isEqualTo("true");

        crearCola("{\"nombre\":\"duocconecta-prueba\"}").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una cola de la topología base no se elimina: 409 sin llamar a AWS")
    void colaProtegida() throws Exception {
        mockMvc.perform(delete("/api/v1/mensajeria/colas/duocconecta-auditoria-eventos").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail", containsString("topología base")));
        mockMvc.perform(delete("/api/v1/mensajeria/topicos/duocconecta-eventos").with(admin()))
                .andExpect(status().isConflict());
        verify(sqs, never()).deleteQueue(any(DeleteQueueRequest.class));
    }

    @Test
    @DisplayName("Una cola que no existe: 404")
    void colaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/mensajeria/colas/duocconecta-no-existe").with(admin()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/mensajeria/colas/duocconecta-no-existe").with(admin()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions crearCola(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/mensajeria/colas").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static RequestPostProcessor admin() {
        return token("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }

    private static RequestPostProcessor token(String... roles) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        for (String rol : roles) {
            authorities.add(new SimpleGrantedAuthority(rol));
        }
        return jwt().jwt(t -> t.claim("oid", "oid-admin").claim("email", "admin@duocuc.cl").claim("name", "Admin"))
                .authorities(authorities);
    }
}
