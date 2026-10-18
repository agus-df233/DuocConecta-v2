package cl.duoc.duocconecta.comun.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Publicación en RabbitMQ sin broker, y el publicador que manda a los dos brokers. */
class PublicacionRabbitTest {

    private final RabbitTemplate plantilla = mock(RabbitTemplate.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private static PropiedadesMensajeria propiedades() {
        PropiedadesMensajeria propiedades = new PropiedadesMensajeria();
        propiedades.setOrigen("ms-proyectos");
        propiedades.getRabbit().setHabilitada(true);
        propiedades.getRabbit().setExchange("duocconecta.eventos");
        return propiedades;
    }

    private static EventoDuocConecta evento() {
        return EventoDuocConecta.nuevo(TiposDeEvento.PROYECTO_OCULTADO, "oid-admin", "oid-duena", "p-1", "p-1", "Contenido ajeno");
    }

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Publica en el exchange topic con el tipo como routing key, persistente y con sus cabeceras")
    void publicaConRoutingKeyYCabeceras() {
        MDC.put(AtributosMensaje.CLAVE_MDC, "demo-rabbit");
        EventoDuocConecta evento = evento();

        new PublicadorRabbit(plantilla, jsonMapper, propiedades()).publicar(evento);

        ArgumentCaptor<Message> mensaje = ArgumentCaptor.forClass(Message.class);
        verify(plantilla).send(eq("duocconecta.eventos"), eq("proyecto.ocultado"), mensaje.capture(), any(CorrelationData.class));
        var cabeceras = mensaje.getValue().getMessageProperties();
        assertThat(cabeceras.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(cabeceras.getMessageId()).isEqualTo(evento.idEvento());
        assertThat((String) cabeceras.getHeader("tipo")).isEqualTo("proyecto.ocultado");
        assertThat((String) cabeceras.getHeader("origen")).isEqualTo("ms-proyectos");
        assertThat((String) cabeceras.getHeader("X-Request-Id")).isEqualTo("demo-rabbit");
        String cuerpo = new String(mensaje.getValue().getBody(), StandardCharsets.UTF_8);
        // Solo identificadores: nunca un correo.
        assertThat(cuerpo).contains(evento.idEvento()).contains("\"origen\":\"ms-proyectos\"").doesNotContain("@");
    }

    @Test
    @DisplayName("Los dos brokers: si RabbitMQ falla, SNS igual publica y el error no corta el negocio")
    void unBrokerCaidoNoAfectaAlOtro() {
        PublicadorEventos sns = mock(PublicadorEventos.class);
        PublicadorEventos rabbit = mock(PublicadorEventos.class);
        doThrow(new IllegalStateException("broker caído")).when(rabbit).publicar(any());
        EventoDuocConecta evento = evento();

        new PublicadorCompuesto(List.of(rabbit, sns)).publicar(evento);

        verify(sns).publicar(evento);
    }

    @Test
    @DisplayName("Si fallan todos los brokers, el error se propaga: el evento no llegó a ninguna parte")
    void todosCaidosPropaga() {
        PublicadorEventos sns = mock(PublicadorEventos.class);
        PublicadorEventos rabbit = mock(PublicadorEventos.class);
        doThrow(new IllegalStateException("sns caído")).when(sns).publicar(any());
        doThrow(new IllegalStateException("rabbit caído")).when(rabbit).publicar(any());

        assertThatThrownBy(() -> new PublicadorCompuesto(List.of(sns, rabbit)).publicar(evento()))
                .hasMessageContaining("sns caído");
    }

    @Test
    @DisplayName("Un broker caído al publicar no rompe la plantilla de los demás eventos")
    void plantillaQueFallaPropagaEnSolitario() {
        doThrow(new org.springframework.amqp.AmqpConnectException(new RuntimeException("sin conexión")))
                .when(plantilla).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new PublicadorRabbit(plantilla, jsonMapper, propiedades()).publicar(evento()))
                .isInstanceOf(org.springframework.amqp.AmqpConnectException.class);
    }
}
