package cl.duoc.duocconecta.comun.eventos;

import java.util.Set;

/**
 * Catálogo de eventos: la única fuente de sus nombres.
 *
 * <p>Formato {@code categoria.hecho}, en minúsculas y sin tildes: el filtro de SNS compara texto
 * exacto y una tilde de más haría que el evento no le llegue a nadie.</p>
 */
public final class TiposDeEvento {

    public static final String PROYECTO_PUBLICADO = "proyecto.publicado";
    public static final String PROYECTO_ACTUALIZADO = "proyecto.actualizado";
    public static final String PROYECTO_ELIMINADO = "proyecto.eliminado";
    public static final String PROYECTO_COMENTADO = "proyecto.comentado";
    public static final String PROYECTO_COLABORADOR_AGREGADO = "proyecto.colaborador_agregado";
    public static final String PROYECTO_OCULTADO = "proyecto.ocultado";
    public static final String PROYECTO_RESTAURADO = "proyecto.restaurado";
    public static final String PROYECTO_COMENTARIO_ELIMINADO = "proyecto.comentario_eliminado";

    public static final String COLABORACION_SOLICITADA = "colaboracion.solicitada";
    public static final String COLABORACION_ACEPTADA = "colaboracion.aceptada";
    public static final String COLABORACION_RECHAZADA = "colaboracion.rechazada";

    /** Todos los tipos conocidos: un consumidor descarta como no recuperable lo que no esté aquí. */
    public static final Set<String> CONOCIDOS = Set.of(
            PROYECTO_PUBLICADO, PROYECTO_ACTUALIZADO, PROYECTO_ELIMINADO, PROYECTO_COMENTADO,
            PROYECTO_COLABORADOR_AGREGADO, PROYECTO_OCULTADO, PROYECTO_RESTAURADO,
            PROYECTO_COMENTARIO_ELIMINADO,
            COLABORACION_SOLICITADA, COLABORACION_ACEPTADA, COLABORACION_RECHAZADA);

    private TiposDeEvento() {
    }
}
