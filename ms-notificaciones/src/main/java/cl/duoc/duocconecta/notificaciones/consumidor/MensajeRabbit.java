package cl.duoc.duocconecta.notificaciones.consumidor;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;

/**
 * Lo que un consumidor necesita de un mensaje de RabbitMQ, ya leído: el cuerpo, el
 * {@code X-Request-Id} y el número de entrega.
 *
 * @param intento 1 en la primera entrega. La cola quorum informa las entregas anteriores en
 *                {@code x-delivery-count}.
 */
public record MensajeRabbit(String cuerpo, String idPeticion, String intento, long etiqueta) {

    public static MensajeRabbit de(Message mensaje) {
        var propiedades = mensaje.getMessageProperties();
        Object id = propiedades.getHeader(AtributosMensaje.ID_PETICION);
        Object anteriores = propiedades.getHeader("x-delivery-count");
        long previas = anteriores instanceof Number n ? n.longValue() : 0;
        return new MensajeRabbit(new String(mensaje.getBody(), StandardCharsets.UTF_8),
                id == null ? null : id.toString(), String.valueOf(previas + 1), propiedades.getDeliveryTag());
    }
}
