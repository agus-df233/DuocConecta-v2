package cl.duoc.duocconecta.mensajeria.dto;

/** Una tarea de SQS que devuelve los mensajes de una DLQ a su cola de origen. */
public record RedriveRespuesta(String colaDescarte, String tarea) {
}
