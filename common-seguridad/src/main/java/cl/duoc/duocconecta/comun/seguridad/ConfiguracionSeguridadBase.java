package cl.duoc.duocconecta.comun.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Configuración de seguridad compartida por todos los servicios de DuocConecta.
 *
 * <p>Cada servicio la incorpora con {@code @Import(ConfiguracionSeguridadBase.class)}. Se importa
 * de forma explícita y no por escaneo de componentes porque este módulo vive en otro paquete
 * que las aplicaciones.</p>
 *
 * <p>Aquí se arma el decodificador del JWT con todas las validaciones encadenadas: firma,
 * vigencia, emisor y audiencia.</p>
 */
@Configuration
@EnableConfigurationProperties(PropiedadesSeguridad.class)
public class ConfiguracionSeguridadBase {

    /**
     * URL del emisor del tenant de Azure AD. De aquí se descubre la ubicación de las claves
     * públicas con las que se verifica la firma de los tokens.
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String issuerUri;

    /** Ubicación directa de las claves públicas. Se usa en los tests, donde no hay discovery. */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}")
    private String jwkSetUri;

    /** Traduce el dominio del correo al rol de la plataforma. */
    @Bean
    public ResolvedorRol resolvedorRol(PropiedadesSeguridad propiedades) {
        return new ResolvedorRol(propiedades);
    }

    /** Lee oid, correo, nombre y App Roles desde el token. */
    @Bean
    public ClaimsDelToken claimsDelToken(PropiedadesSeguridad propiedades) {
        return new ClaimsDelToken(propiedades);
    }

    /** Convierte el token en una autenticación con el rol como authority. */
    @Bean
    public ConversorRolesJwt conversorRolesJwt(ClaimsDelToken claimsDelToken, ResolvedorRol resolvedorRol) {
        return new ConversorRolesJwt(claimsDelToken, resolvedorRol);
    }

    /** Componente reutilizable para obtener el usuario autenticado desde cualquier capa. */
    @Bean
    public UsuarioActual usuarioActual(ClaimsDelToken claimsDelToken, ResolvedorRol resolvedorRol) {
        return new UsuarioActual(claimsDelToken, resolvedorRol);
    }

    /**
     * Arma el decodificador del JWT con la cadena completa de validaciones.
     *
     * <p>Spring valida por defecto la firma, la vigencia y el emisor. A eso se le suma
     * {@link ValidadorAudiencia}, que comprueba que el token haya sido emitido para esta API.</p>
     *
     * <p>Cuando hay {@code issuer-uri} se usa el descubrimiento OIDC, que consulta el endpoint de
     * metadatos del tenant al arrancar. Cuando solo hay {@code jwk-set-uri} se construye el
     * decodificador directo, sin llamadas de red en el arranque: es lo que usan los tests.</p>
     */
    @Bean
    public JwtDecoder jwtDecoder(PropiedadesSeguridad propiedades) {
        NimbusJwtDecoder decodificador = construirDecodificador();
        decodificador.setJwtValidator(validadores(propiedades));
        return decodificador;
    }

    /**
     * Elige cómo obtener las claves públicas: por descubrimiento del emisor o directo del JWK Set.
     */
    private NimbusJwtDecoder construirDecodificador() {
        if (issuerUri != null && !issuerUri.isBlank()) {
            return (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
        }
        if (jwkSetUri != null && !jwkSetUri.isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        }
        throw new IllegalStateException(
                "Falta configurar la validación del token. Definí AZURE_TENANT_ID para que se arme "
                        + "spring.security.oauth2.resourceserver.jwt.issuer-uri, o bien un jwk-set-uri.");
    }

    /**
     * Encadena las validaciones estándar del emisor con la de audiencia propia.
     */
    private OAuth2TokenValidator<Jwt> validadores(PropiedadesSeguridad propiedades) {
        OAuth2TokenValidator<Jwt> porDefecto = (issuerUri != null && !issuerUri.isBlank())
                ? JwtValidators.createDefaultWithIssuer(issuerUri)
                : JwtValidators.createDefault();

        String audiencia = propiedades.getAudiencia();
        if (audiencia == null || audiencia.isBlank()) {
            throw new IllegalStateException(
                    "Falta duocconecta.seguridad.audiencia (variable AZURE_CLIENT_ID). Sin ella se "
                            + "aceptaría cualquier token del tenant, aunque fuera de otra aplicación.");
        }

        return new DelegatingOAuth2TokenValidator<>(porDefecto, new ValidadorAudiencia(audiencia));
    }
}
