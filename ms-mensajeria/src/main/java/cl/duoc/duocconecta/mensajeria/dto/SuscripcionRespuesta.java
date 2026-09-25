package cl.duoc.duocconecta.mensajeria.dto;

/**
 * Una suscripción de cola a tópico.
 *
 * @param id identificador corto (el final del ARN), el que se usa en {@code DELETE /suscripciones/{id}}
 */
public record SuscripcionRespuesta(String id, String arn, String topico, String cola, String filtro,
                                   boolean entregaCruda, String colaNoEntregados, boolean protegida) {
}
