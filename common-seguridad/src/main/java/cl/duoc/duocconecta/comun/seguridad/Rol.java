package cl.duoc.duocconecta.comun.seguridad;

/** Roles de la plataforma. Salen del dominio del correo o de los App Roles. Ver {@link ResolvedorRol}. */
public enum Rol {

    /** {@code @duocuc.cl} */
    ESTUDIANTE,

    /** {@code @profesor.duoc.cl} */
    PROFESOR,

    /** {@code @duoc.cl} */
    ACADEMICO;

    /** Prefijo que Spring Security espera para que {@code hasRole(...)} funcione. */
    public static final String PREFIJO_AUTHORITY = "ROLE_";

    /** Por ejemplo, {@code ROLE_ESTUDIANTE}. */
    public String comoAuthority() {
        return PREFIJO_AUTHORITY + name();
    }
}
