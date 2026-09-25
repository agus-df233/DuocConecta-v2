package cl.duoc.duocconecta.notificaciones.dto;

/**
 * Un mensaje en una DLQ, leído sin sacarlo de ahí.
 *
 * @param motivo      el que puso el consumidor al descartarlo; nulo si llegó por reintentos agotados
 * @param recepciones cuántas veces se entregó (en la DLQ también cuenta cada lectura)
 */
public record MensajeDescartado(String idMensaje, String cuerpo, String motivo, String idPeticion,
                                int recepciones) {
}
