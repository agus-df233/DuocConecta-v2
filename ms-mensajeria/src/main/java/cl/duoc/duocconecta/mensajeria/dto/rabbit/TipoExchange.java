package cl.duoc.duocconecta.mensajeria.dto.rabbit;

/** Tipos de exchange que se pueden crear desde la API. */
public enum TipoExchange {
    /** Entrega a la cola cuyo binding tiene exactamente la routing key. */
    DIRECT,
    /** Entrega según patrones: {@code *} reemplaza una palabra y {@code #} cero o más. */
    TOPIC,
    /** Entrega a todas las colas enlazadas, sin mirar la routing key. */
    FANOUT
}
