package cl.duoc.duocconecta.notificaciones.consumidor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.duocconecta.notificaciones.repository.RepositorioAvisos;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * La tabla de confirmación cumplida en RabbitMQ: ACK, NACK con reencolado (reintento) y NACK sin
 * reencolar (a la DLQ por el dead-letter exchange). El canal es un doble: no hace falta broker.
 */
@SpringBootTest(properties = "duocconecta.mensajeria.permitir-fallos-simulados=true")
@ActiveProfiles("test")
class ConfirmacionRabbitTests {

    private static final long ETIQUETA = 42L;

    @Autowired
    private ProcesadorDeMensajes procesador;

    @Autowired
    private RepositorioAvisos avisos;

    @Autowired
    private cl.duoc.duocconecta.notificaciones.service.ServicioAvisos servicioAvisos;

    private static String evento(String id, String detalle) {
        return """
                {"idEvento":"%s","tipo":"proyecto.comentado","version":1,"origen":"ms-proyectos",
                 "ocurridoEn":"2026-10-17T12:00:00Z","actorId":"oid-a","destinatarioId":"oid-b",
                 "recursoId":"r","proyectoId":"p","detalle":%s}"""
                .formatted(id, detalle == null ? "null" : "\"" + detalle + "\"");
    }

    private static ConfirmacionRabbit confirmacion(Channel canal) {
        return new ConfirmacionRabbit(canal, ETIQUETA, 3, "duocconecta.notificaciones.proyectos.descartes");
    }

    @Test
    @DisplayName("Procesado: basicAck")
    void procesadoHaceAck() throws Exception {
        Channel canal = mock(Channel.class);
        procesador.procesar("proyectos-rabbit-test", evento(UUID.randomUUID().toString(), null), "r-1", "1",
                confirmacion(canal), e -> { });
        verify(canal).basicAck(ETIQUETA, false);
        verify(canal, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("JSON inválido: basicNack SIN reencolar → RabbitMQ lo manda al DLX")
    void noProcesableVaAlDlx() throws Exception {
        Channel canal = mock(Channel.class);
        procesador.procesar("proyectos-rabbit-test", "esto no es json", null, "1", confirmacion(canal), e -> { });
        verify(canal).basicNack(ETIQUETA, false, false);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("Fallo recuperable: basicNack CON reencolado; la cola quorum cuenta la entrega")
    void recuperableReencola() throws Exception {
        Channel canal = mock(Channel.class);
        procesador.procesar("proyectos-rabbit-test",
                evento(UUID.randomUUID().toString(), ProcesadorDeMensajes.DETALLE_FALLO_SIMULADO), null, "1",
                confirmacion(canal), e -> { });
        verify(canal).basicNack(ETIQUETA, false, true);
        verify(canal, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("En el último intento también reencola: es el x-delivery-limit el que lo manda al DLX")
    void ultimoIntentoTambienReencola() throws Exception {
        Channel canal = mock(Channel.class);
        procesador.procesar("proyectos-rabbit-test",
                evento(UUID.randomUUID().toString(), ProcesadorDeMensajes.DETALLE_FALLO_SIMULADO), null, "3",
                confirmacion(canal), e -> { });
        verify(canal).basicNack(ETIQUETA, false, true);
    }

    @Test
    @DisplayName("El mismo evento por SQS y por RabbitMQ: un solo aviso, el segundo se confirma como duplicado")
    void dosBrokersUnSoloAviso() throws Exception {
        String id = UUID.randomUUID().toString();
        String cuerpo = evento(id, null);
        Channel canal = mock(Channel.class);
        java.util.function.Consumer<cl.duoc.duocconecta.comun.eventos.EventoDuocConecta> crearAviso =
                e -> servicioAvisos.crear(e, "Comentaron tu proyecto");

        procesador.procesar("proyectos-dos-brokers", cuerpo, null, "1",
                new ConfirmacionSqs(mock(io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement.class),
                        mock(io.awspring.cloud.sqs.listener.Visibility.class), null, "proyectos", cuerpo, null, 3, "dlq"),
                crearAviso);
        procesador.procesar("proyectos-dos-brokers", cuerpo, null, "1", confirmacion(canal), crearAviso);

        assertThat(avisos.countByIdEvento(id)).isEqualTo(1);
        verify(canal).basicAck(ETIQUETA, false);
    }

    @Test
    @DisplayName("MensajeRabbit lee el cuerpo, el X-Request-Id y el número de entrega (x-delivery-count + 1)")
    void leeElMensaje() {
        MessageProperties propiedades = new MessageProperties();
        propiedades.setHeader("X-Request-Id", "abc");
        propiedades.setHeader("x-delivery-count", 2L);
        propiedades.setDeliveryTag(7L);
        MensajeRabbit m = MensajeRabbit.de(new Message("{}".getBytes(StandardCharsets.UTF_8), propiedades));
        assertThat(m.cuerpo()).isEqualTo("{}");
        assertThat(m.idPeticion()).isEqualTo("abc");
        assertThat(m.intento()).isEqualTo("3");
        assertThat(m.etiqueta()).isEqualTo(7L);
    }
}
