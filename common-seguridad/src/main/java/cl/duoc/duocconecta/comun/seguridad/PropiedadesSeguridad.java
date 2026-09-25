package cl.duoc.duocconecta.comun.seguridad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración de seguridad, bajo {@code duocconecta.seguridad} en el YAML.
 *
 * <p>El mapa de dominios a roles vive aquí y no en el código: sumar un dominio no recompila nada.</p>
 */
@ConfigurationProperties(prefix = "duocconecta.seguridad")
public class PropiedadesSeguridad {

    /** Client-id de la API. Un token de otra aplicación se rechaza aunque la firma sea válida. */
    private String audiencia;

    /**
     * Claims donde buscar el correo del usuario, en orden de preferencia.
     *
     * <p>Azure AD emite distintos claims según el tipo de cuenta y la configuración del tenant,
     * por eso se prueba una cadena de alternativas en vez de uno solo.</p>
     */
    private List<String> claimsCorreo = List.of("preferred_username", "email", "upn", "unique_name");

    /** Claims donde buscar el nombre para mostrar del usuario, en orden de preferencia. */
    private List<String> claimsNombre = List.of("name", "given_name", "preferred_username");

    /** Claim que contiene los App Roles asignados en Azure AD, si el tenant los usa. */
    private String claimRoles = "roles";

    /**
     * Mapa de dominio de correo a rol. La clave es el dominio sin arroba, en minúsculas.
     *
     * <p>Se usa {@link LinkedHashMap} para que el orden declarado en el YAML se conserve,
     * lo que hace más legible el log de arranque.</p>
     */
    private Map<String, Rol> dominios = new LinkedHashMap<>();

    public String getAudiencia() {
        return audiencia;
    }

    public void setAudiencia(String audiencia) {
        this.audiencia = audiencia;
    }

    public List<String> getClaimsCorreo() {
        return claimsCorreo;
    }

    public void setClaimsCorreo(List<String> claimsCorreo) {
        this.claimsCorreo = claimsCorreo;
    }

    public List<String> getClaimsNombre() {
        return claimsNombre;
    }

    public void setClaimsNombre(List<String> claimsNombre) {
        this.claimsNombre = claimsNombre;
    }

    public String getClaimRoles() {
        return claimRoles;
    }

    public void setClaimRoles(String claimRoles) {
        this.claimRoles = claimRoles;
    }

    public Map<String, Rol> getDominios() {
        return dominios;
    }

    public void setDominios(Map<String, Rol> dominios) {
        this.dominios = dominios;
    }
}
