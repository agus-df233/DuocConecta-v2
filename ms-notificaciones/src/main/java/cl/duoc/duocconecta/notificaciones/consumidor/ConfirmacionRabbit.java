package cl.duoc.duocconecta.notificaciones.consumidor;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Confirmación en RabbitMQ, con ACK manual sobre el canal.
 *
 * <ul>
 *   <li>Confirmar: {@code basicAck}.</li>
 *   <li>Reintentar: {@code basicNack} con reencolado. La cola quorum cuenta las entregas
 *       ({@code x-delivery-count}) y, al pasar {@code x-delivery-limit}, la manda sola al DLX.</li>
 *   <li>Descartar: {@code basicNack} SIN reencolar. RabbitMQ lo manda directo al dead-letter
 *       exchange: a diferencia de SQS, no hace falta copiarlo a mano.</li>
 * </ul>
 */
public record ConfirmacionRabbit(Channel canal, long etiqueta, int maxEntregas, String dlq)
        implements Confirmacion {

    @Override
    public void confirmar() {
        ejecutar(() -> canal.basicAck(etiqueta, false));
    }

    /** RabbitMQ reencola de inmediato: el límite de entregas es lo que evita un ciclo infinito. */
    @Override
    public void reintentar(int segundos) {
        ejecutar(() -> canal.basicNack(etiqueta, false, true));
    }

    @Override
    public void descartar(String motivo) {
        ejecutar(() -> canal.basicNack(etiqueta, false, false));
    }

    private interface OperacionCanal {
        void ejecutar() throws IOException;
    }

    private static void ejecutar(OperacionCanal operacion) {
        try {
            operacion.ejecutar();
        } catch (IOException error) {
            throw new UncheckedIOException("No se pudo responder al broker", error);
        }
    }
}
