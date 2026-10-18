package cl.duoc.duocconecta.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** Cada token se verifica con las claves y la audiencia de SU emisor; uno desconocido no pasa. */
class DecodificadorMultiEmisorTest {

    private static final String ENTRA = "https://login.microsoftonline.com/tenant/v2.0";
    private static final String COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_pool";

    private final KeyPair clavesEntra = generar();
    private final KeyPair clavesCognito = generar();
    private final AtomicInteger construidos = new AtomicInteger();

    private final DecodificadorMultiEmisor decodificador = new DecodificadorMultiEmisor(Map.of(
            ENTRA, fabrica(clavesEntra, ENTRA, "api-entra"),
            COGNITO, fabrica(clavesCognito, COGNITO, "app-client-cognito")));

    private static KeyPair generar() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Supplier<JwtDecoder> fabrica(KeyPair claves, String emisor, String audiencia) {
        return () -> {
            construidos.incrementAndGet();
            NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey((RSAPublicKey) claves.getPublic()).build();
            d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(emisor), new ValidadorAudiencia(audiencia)));
            return d;
        };
    }

    private static String firmar(KeyPair claves, String emisor, String audiencia) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(emisor).audience(audiencia).subject("x")
                .claim("email", "ana@duocuc.cl").expirationTime(Date.from(Instant.now().plusSeconds(600))).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(claves.getPrivate()));
        return jwt.serialize();
    }

    @Test
    @DisplayName("Un token de Entra ID y uno de Cognito se aceptan, cada uno con su audiencia")
    void aceptaLosDosEmisores() throws Exception {
        assertThat(decodificador.decode(firmar(clavesEntra, ENTRA, "api-entra")).getSubject()).isEqualTo("x");
        assertThat(decodificador.decode(firmar(clavesCognito, COGNITO, "app-client-cognito")).getSubject()).isEqualTo("x");
    }

    @Test
    @DisplayName("Un emisor que no está en la lista: rechazado (401)")
    void rechazaEmisorDesconocido() throws Exception {
        assertThatThrownBy(() -> decodificador.decode(firmar(generar(), "https://otro.example", "api-entra")))
                .isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("Un token que dice venir de Cognito pero está firmado con otra clave: rechazado")
    void rechazaEmisorFalsificado() throws Exception {
        assertThatThrownBy(() -> decodificador.decode(firmar(clavesEntra, COGNITO, "app-client-cognito")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("La audiencia de un emisor no sirve para el otro")
    void audienciaCruzadaRechazada() throws Exception {
        assertThatThrownBy(() -> decodificador.decode(firmar(clavesCognito, COGNITO, "api-entra")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Texto que no es un JWT: rechazado como token inválido")
    void rechazaBasura() {
        assertThatThrownBy(() -> decodificador.decode("esto-no-es-un-jwt")).isInstanceOf(BadJwtException.class);
    }

    @Test
    @DisplayName("Cada decodificador se construye una sola vez, con el primer token de su emisor")
    void construccionPerezosa() throws Exception {
        assertThat(construidos.get()).isZero();
        decodificador.decode(firmar(clavesEntra, ENTRA, "api-entra"));
        decodificador.decode(firmar(clavesEntra, ENTRA, "api-entra"));
        assertThat(construidos.get()).isEqualTo(1);
    }
}
