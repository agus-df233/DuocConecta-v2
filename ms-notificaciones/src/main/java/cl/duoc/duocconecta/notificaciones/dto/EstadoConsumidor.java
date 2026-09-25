package cl.duoc.duocconecta.notificaciones.dto;

/** Un listener de SQS y si está consumiendo. */
public record EstadoConsumidor(String id, boolean activo) {
}
