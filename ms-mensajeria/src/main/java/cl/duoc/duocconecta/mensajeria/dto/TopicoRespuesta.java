package cl.duoc.duocconecta.mensajeria.dto;

/** Un tópico SNS y cuántas suscripciones tiene. */
public record TopicoRespuesta(String nombre, String arn, int suscripciones, boolean protegido) {
}
