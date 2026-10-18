package cl.duoc.duocconecta.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Un token de otra aplicación del mismo IdP se rechaza aunque su firma sea válida. */
class ValidadorAudienciaTest {

    private static final String API = "11111111-2222-3333-4444-555555555555";

    private final ValidadorAudiencia validador = new ValidadorAudiencia(API);

    private static Jwt tokenCon(List<String> audiencias) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "x");
        if (audiencias != null) {
            builder.audience(audiencias);
        }
        return builder.build();
    }

    @Test
    @DisplayName("La audiencia de la API se acepta")
    void audienciaCorrecta() {
        assertThat(validador.validate(tokenCon(List.of(API))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("También se acepta la forma api://<client-id>")
    void audienciaConPrefijoApi() {
        assertThat(validador.validate(tokenCon(List.of("api://" + API))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Basta con que una de varias audiencias sea la de la API")
    void variasAudiencias() {
        assertThat(validador.validate(tokenCon(List.of("otra-app", API))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Un ID token del SPA (otra audiencia) se rechaza como invalid_token")
    void audienciaDeOtraAplicacion() {
        OAuth2TokenValidatorResult resultado = validador.validate(tokenCon(List.of("client-id-del-spa")));
        assertThat(resultado.hasErrors()).isTrue();
        assertThat(resultado.getErrors()).first()
                .satisfies(e -> {
                    assertThat(e.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN);
                    // El mensaje orienta a quien depura, sin revelar la audiencia recibida.
                    assertThat(e.getDescription()).contains("scope de la API").doesNotContain("client-id-del-spa");
                });
    }

    @Test
    @DisplayName("Sin claim aud, se rechaza")
    void sinAudiencia() {
        assertThat(validador.validate(tokenCon(null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Un prefijo parecido no basta: la comparación es exacta")
    void audienciaParecidaNoBasta() {
        assertThat(validador.validate(tokenCon(List.of(API + "-falso", "x" + API))).hasErrors()).isTrue();
    }
}
