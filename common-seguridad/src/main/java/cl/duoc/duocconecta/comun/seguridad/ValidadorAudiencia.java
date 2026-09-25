package cl.duoc.duocconecta.comun.seguridad;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Verifica que el token sea para esta API y no para otra aplicación del tenant.
 *
 * <p>Spring valida firma, vigencia y emisor, pero <strong>no la audiencia</strong>: sin esto se
 * aceptaría cualquier token del tenant. Es el ataque de "confused deputy".</p>
 *
 * <p>Suele fallar cuando el frontend pide solo {@code openid profile}: Microsoft devuelve un ID
 * token cuya audiencia es el SPA, no la API.</p>
 */
public class ValidadorAudiencia implements OAuth2TokenValidator<Jwt> {

    /** El client-id del registro de la API en Azure AD. */
    private final String audienciaEsperada;

    public ValidadorAudiencia(String audienciaEsperada) {
        this.audienciaEsperada = audienciaEsperada;
    }

    /**
     * El claim {@code aud} puede traer varios valores. Se acepta también {@code api://<client-id>},
     * que es la forma que usa Azure AD según cómo esté configurado el Application ID URI.
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiencias = token.getAudience();

        if (audiencias != null
                && (audiencias.contains(audienciaEsperada)
                    || audiencias.contains("api://" + audienciaEsperada))) {
            return OAuth2TokenValidatorResult.success();
        }

        // La audiencia recibida no va en el mensaje: iría al cliente.
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "El token no fue emitido para esta API. Verifica que el cliente esté pidiendo el "
                        + "scope de la API (api://<client-id>/access_as_user) y no solo openid/profile.",
                null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
