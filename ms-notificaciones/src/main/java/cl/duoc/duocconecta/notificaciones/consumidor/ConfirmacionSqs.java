package cl.duoc.duocconecta.notificaciones.consumidor;

import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;

/**
 * Confirmación en SQS. ACK es borrar el mensaje; reintentar es no borrarlo y alargar su
 * visibilidad. SQS no tiene un "NACK sin reencolar": el descarte copia el mensaje a la DLQ a mano
 * ({@link EnvioADescarte}) y después lo borra.
 */
public record ConfirmacionSqs(Acknowledgement ack, Visibility visibilidad, EnvioADescarte envio,
                              String claveCola, String cuerpo, String idPeticion,
                              int maxEntregas, String dlq) implements Confirmacion {

    @Override
    public void confirmar() {
        ack.acknowledge();
    }

    /** En el último intento no se toca la visibilidad: al vencer, la redrive lo lleva a la DLQ. */
    @Override
    public void reintentar(int segundos) {
        if (segundos > 0) {
            visibilidad.changeTo(segundos);
        }
    }

    @Override
    public void descartar(String motivo) throws Exception {
        envio.enviar(claveCola, cuerpo, motivo, idPeticion);
        ack.acknowledge();
    }
}
