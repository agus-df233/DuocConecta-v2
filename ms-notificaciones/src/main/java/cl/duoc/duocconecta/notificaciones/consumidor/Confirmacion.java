package cl.duoc.duocconecta.notificaciones.consumidor;

/**
 * Lo que el procesador le pide al broker según cómo terminó el mensaje. Así la tabla de decisiones
 * de {@link ProcesadorDeMensajes} es una sola para SQS y para RabbitMQ: cada broker la cumple a su
 * manera.
 */
public interface Confirmacion {

    /** ACK: el mensaje se procesó (o ya estaba procesado) y sale de la cola. */
    void confirmar();

    /**
     * Sin ACK: que el broker lo vuelva a entregar.
     *
     * @param segundos espera antes de la próxima entrega; 0 en el último intento, cuando ya no
     *                 conviene esperar porque el broker lo va a mandar a la DLQ
     */
    void reintentar(int segundos);

    /**
     * NACK sin reencolar: el mensaje no tiene arreglo y va a la DLQ con el motivo.
     *
     * @throws Exception si no se pudo; entonces queda para que el broker lo reintente y, al agotar
     *                   las entregas, lo mande él mismo a la DLQ
     */
    void descartar(String motivo) throws Exception;

    /** Entregas antes de que el broker mande el mensaje a la DLQ por su cuenta. */
    int maxEntregas();

    /** Nombre de la DLQ, para el log. */
    String dlq();
}
