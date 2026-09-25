package cl.duoc.duocconecta.comun.seguridad;

/** Expresiones de {@code @PreAuthorize} compartidas, para no repetir texto entre servicios. */
public final class ReglasDeAcceso {

    /** Cualquier persona de la comunidad con dominio autorizado. */
    public static final String ROLES_DE_LA_PLATAFORMA = "hasAnyRole('ESTUDIANTE', 'PROFESOR', 'ACADEMICO')";

    /** Solo quien tiene el rol de administración, además de su rol de dominio. */
    public static final String SOLO_ADMINISTRADOR = "hasRole('ADMINISTRADOR')";

    private ReglasDeAcceso() {
    }
}
