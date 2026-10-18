package cl.duoc.duocconecta.notificaciones.dto;

/** Un consumidor, de qué broker es y si está consumiendo. */
public record EstadoConsumidor(String broker, String id, boolean activo) {
}
