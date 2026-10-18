package cl.duoc.duocconecta.notificaciones;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * La pausa de la demo de desacoplamiento detiene los dos brokers. Si solo parara SQS, RabbitMQ
 * entregaría el mismo evento y el aviso llegaría igual.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PausaDeConsumidoresTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RabbitListenerEndpointRegistry registroRabbit;

    /** Simulado para que reanudar no encienda los listeners reales de SQS, que irían a AWS. */
    @MockitoBean
    private MessageListenerContainerRegistry registroSqs;

    private static RequestPostProcessor admin() {
        return jwt().jwt(t -> t.claim("oid", "oid-admin").claim("email", "admin@duocuc.cl"))
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"),
                        new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")));
    }

    @Test
    @DisplayName("Pausar y reanudar llegan a RabbitMQ, y el estado lista los consumidores de los dos brokers")
    void pausaCubreLosDosBrokers() throws Exception {
        MessageListenerContainer contenedor = Mockito.mock(MessageListenerContainer.class);
        @SuppressWarnings("unchecked")
        io.awspring.cloud.sqs.listener.MessageListenerContainer<Object> sqs =
                Mockito.mock(io.awspring.cloud.sqs.listener.MessageListenerContainer.class);
        when(sqs.getId()).thenReturn("proyectos");
        when(registroSqs.getListenerContainers()).thenReturn(List.of(sqs));
        when(registroRabbit.getListenerContainerIds()).thenReturn(Set.of("rabbit-proyectos"));
        when(registroRabbit.getListenerContainer("rabbit-proyectos")).thenReturn(contenedor);

        mockMvc.perform(get("/api/v1/notificaciones/admin/consumidores").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].broker").value("RabbitMQ"))
                .andExpect(jsonPath("$[0].id").value("rabbit-proyectos"))
                .andExpect(jsonPath("$[1].broker").value("SQS"));

        mockMvc.perform(post("/api/v1/notificaciones/admin/consumidores/pausa").with(admin())).andExpect(status().isOk());
        verify(registroRabbit).stop();
        verify(sqs).stop();
        mockMvc.perform(post("/api/v1/notificaciones/admin/consumidores/reanudar").with(admin())).andExpect(status().isOk());
        verify(registroRabbit).start();
        verify(sqs).start();
    }
}
