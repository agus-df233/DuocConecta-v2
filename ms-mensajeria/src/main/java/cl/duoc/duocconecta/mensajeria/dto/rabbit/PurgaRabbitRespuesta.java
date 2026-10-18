package cl.duoc.duocconecta.mensajeria.dto.rabbit;

/** Resultado de vaciar una cola de RabbitMQ: cuántos mensajes se borraron. */
public record PurgaRabbitRespuesta(String cola, int borrados) {
}
