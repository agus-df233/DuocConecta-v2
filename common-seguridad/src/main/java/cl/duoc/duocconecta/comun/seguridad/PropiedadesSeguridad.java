package cl.duoc.duocconecta.comun.seguridad;

import java.util.ArrayList;
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
     * <p>Cada IdP emite distintos claims según el tipo de cuenta,
     * por eso se prueba una cadena de alternativas en vez de uno solo.</p>
     */
    private List<String> claimsCorreo = List.of("preferred_username", "email", "upn", "unique_name");

    /** Claims donde buscar el nombre para mostrar del usuario, en orden de preferencia. */
    private List<String> claimsNombre = List.of("name", "given_name", "preferred_username");

    /** Claim con los roles del IdP. Se conserva por compatibilidad: si hay {@link #claimsRoles}, manda esa lista. */
    private String claimRoles = "roles";

    /**
     * Claims con roles o grupos, que se suman: {@code roles} (App Roles de Entra ID) y
     * {@code cognito:groups} (grupos del user pool de Cognito).
     */
    private List<String> claimsRoles = new ArrayList<>();

    /**
     * Emisores además del principal ({@code spring.security.oauth2.resourceserver.jwt.issuer-uri}).
     * Hoy, Cognito para el login con Google. Una entrada sin {@code issuerUri} se ignora.
     */
    private List<Emisor> emisoresAdicionales = new ArrayList<>();

    /**
     * Mapa de dominio de correo a rol. La clave es el dominio sin arroba, en minúsculas.
     *
     * <p>Se usa {@link LinkedHashMap} para que el orden declarado en el YAML se conserve,
     * lo que hace más legible el log de arranque.</p>
     */
    private Map<String, Rol> dominios = new LinkedHashMap<>();

    /**
     * Correos con rol de administrador, además del grupo del IdP. Vacía por defecto:
     * el valor real llega por variable de entorno y nunca se escribe en el repositorio.
     */
    private List<String> administradores = new ArrayList<>();

    /**
     * Correos externos (por ejemplo, Gmail que entra con Google) con el rol que reciben, en la
     * forma {@code correo=ROL}. Fuera de esta lista, un dominio no autorizado sigue con 403.
     */
    private List<String> correosPermitidos = new ArrayList<>();

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

    public List<String> getAdministradores() {
        return administradores;
    }

    public void setAdministradores(List<String> administradores) {
        this.administradores = administradores;
    }

    public List<String> getCorreosPermitidos() {
        return correosPermitidos;
    }

    public void setCorreosPermitidos(List<String> correosPermitidos) {
        this.correosPermitidos = correosPermitidos;
    }

    public List<String> getClaimsRoles() {
        return claimsRoles;
    }

    public void setClaimsRoles(List<String> claimsRoles) {
        this.claimsRoles = claimsRoles;
    }

    public List<Emisor> getEmisoresAdicionales() {
        return emisoresAdicionales;
    }

    public void setEmisoresAdicionales(List<Emisor> emisoresAdicionales) {
        this.emisoresAdicionales = emisoresAdicionales;
    }

    /** Solo las entradas con emisor: las que llegan vacías desde una variable de entorno no cuentan. */
    public List<Emisor> emisoresAdicionalesConfigurados() {
        return emisoresAdicionales.stream()
                .filter(e -> e.getIssuerUri() != null && !e.getIssuerUri().isBlank())
                .toList();
    }

    /**
     * Un emisor de tokens aceptado: su URL, la audiencia que deben traer sus tokens y si se exige
     * {@code email_verified}. Entra ID no emite ese claim; Cognito sí.
     */
    public static class Emisor {

        private String issuerUri;
        private String audiencia;
        private boolean exigirCorreoVerificado;

        public Emisor() {
        }

        public Emisor(String issuerUri, String audiencia, boolean exigirCorreoVerificado) {
            this.issuerUri = issuerUri;
            this.audiencia = audiencia;
            this.exigirCorreoVerificado = exigirCorreoVerificado;
        }

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getAudiencia() {
            return audiencia;
        }

        public void setAudiencia(String audiencia) {
            this.audiencia = audiencia;
        }

        public boolean isExigirCorreoVerificado() {
            return exigirCorreoVerificado;
        }

        public void setExigirCorreoVerificado(boolean exigirCorreoVerificado) {
            this.exigirCorreoVerificado = exigirCorreoVerificado;
        }
    }
}
