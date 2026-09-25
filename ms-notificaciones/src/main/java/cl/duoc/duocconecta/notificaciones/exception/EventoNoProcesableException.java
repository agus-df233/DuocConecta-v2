package cl.duoc.duocconecta.notificaciones.exception;

/**
 * El mensaje no tiene arreglo: reintentarlo daría el mismo resultado. El consumidor lo manda a
 * la DLQ de inmediato y lo confirma, que es el "NACK sin reencolar" de RabbitMQ.
 */
public class EventoNoProcesableException extends RuntimeException {

    public EventoNoProcesableException(String motivo) {
        super(motivo);
    }

    public EventoNoProcesableException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
