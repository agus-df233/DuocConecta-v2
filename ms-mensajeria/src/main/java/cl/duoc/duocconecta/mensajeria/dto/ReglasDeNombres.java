package cl.duoc.duocconecta.mensajeria.dto;

/**
 * Formato de los nombres que acepta la API.
 *
 * <p>Solo recursos con el prefijo {@code duocconecta-}: la API no puede tocar nada ajeno a la
 * plataforma. SQS y SNS no aceptan puntos, y SQS limita el nombre a 80 caracteres: como la DLQ
 * suma {@code -descartes} (10), el nombre base llega a 69.</p>
 */
public final class ReglasDeNombres {

    public static final String PATRON = "^duocconecta-[a-z0-9]+(-[a-z0-9]+)*$";

    public static final String MENSAJE = "Usa el prefijo duocconecta- seguido de minúsculas, números y guiones (sin puntos)";

    public static final int LARGO_MAXIMO = 69;

    /** Prefijo de {@code tipo} para filtrar, por ejemplo {@code proyecto.} o {@code colaboracion.}. */
    public static final String PATRON_FILTRO = "^[a-z]+(_[a-z]+)*\\.$";

    private ReglasDeNombres() {
    }
}
