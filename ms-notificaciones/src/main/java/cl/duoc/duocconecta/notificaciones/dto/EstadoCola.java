package cl.duoc.duocconecta.notificaciones.dto;

/** Una cola de descarte y cuántos mensajes tiene esperando revisión. */
public record EstadoCola(String nombre, long mensajes) {
}
