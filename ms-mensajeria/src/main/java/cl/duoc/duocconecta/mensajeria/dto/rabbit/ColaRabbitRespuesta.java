package cl.duoc.duocconecta.mensajeria.dto.rabbit;

/**
 * Una cola de RabbitMQ con sus contadores.
 *
 * @param mensajes     listos para entregar
 * @param consumidores conectados escuchando
 * @param protegida    parte de la topología base: la API no la elimina
 */
public record ColaRabbitRespuesta(String nombre, long mensajes, long consumidores, String dlq, boolean protegida) {
}
