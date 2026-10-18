package cl.duoc.duocconecta.notificaciones.dto;

/** Una cola de descarte, de qué broker es y cuántos mensajes tiene esperando revisión. */
public record EstadoCola(String broker, String nombre, long mensajes) {
}
