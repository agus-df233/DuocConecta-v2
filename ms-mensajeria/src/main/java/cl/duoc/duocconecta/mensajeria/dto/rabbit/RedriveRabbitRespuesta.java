package cl.duoc.duocconecta.mensajeria.dto.rabbit;

/**
 * Resultado de devolver una DLQ de RabbitMQ a su cola.
 *
 * @param movidos cuántos mensajes volvieron; si quedan más que el tope, se vuelve a llamar
 */
public record RedriveRabbitRespuesta(String colaDescarte, String colaDestino, int movidos) {
}
