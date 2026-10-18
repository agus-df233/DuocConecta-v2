package cl.duoc.duocconecta.mensajeria.dto.rabbit;

/**
 * Formato de los nombres que acepta la API de RabbitMQ. Solo recursos con el prefijo
 * {@code duocconecta.}: la API no puede tocar nada ajeno a la plataforma.
 */
public final class ReglasRabbit {

    /** {@code duocconecta.} seguido de palabras en minúscula separadas por punto o guion. */
    public static final String PATRON = "^duocconecta\\.[a-z0-9]+([.-][a-z0-9]+)*$";

    public static final String MENSAJE = "Usa el prefijo duocconecta. seguido de minúsculas, números, puntos o guiones";

    /** Routing key o patrón de binding: palabras separadas por punto, con los comodines * y #. */
    public static final String PATRON_ROUTING = "^(#|\\*|[a-z0-9_-]+)(\\.(#|\\*|[a-z0-9_-]+))*$";

    public static final String MENSAJE_ROUTING = "Usa palabras en minúscula separadas por punto; se permiten los comodines * (una palabra) y # (cero o más)";

    private ReglasRabbit() {
    }
}
