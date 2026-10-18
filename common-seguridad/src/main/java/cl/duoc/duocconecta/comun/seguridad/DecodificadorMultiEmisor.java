package cl.duoc.duocconecta.comun.seguridad;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Acepta tokens de varios emisores: Entra ID (login con Microsoft) y Cognito (login con Google).
 *
 * <p>Lee el {@code iss} del token <strong>sin confiar en él</strong>, solo para elegir con qué
 * decodificador verificarlo. Ese decodificador valida la firma con las claves de ESE emisor, la
 * vigencia, el emisor y su audiencia: un token que mienta sobre su emisor no pasa la firma.</p>
 *
 * <p>Los decodificadores se crean la primera vez que llega un token de cada emisor: así el
 * servicio arranca aunque un IdP no responda en ese momento.</p>
 */
public class DecodificadorMultiEmisor implements JwtDecoder {

    private final Map<String, Supplier<JwtDecoder>> fabricas;
    private final Map<String, JwtDecoder> creados = new ConcurrentHashMap<>();

    /** @param fabricas emisor (sin barra final) → cómo construir su decodificador */
    public DecodificadorMultiEmisor(Map<String, Supplier<JwtDecoder>> fabricas) {
        this.fabricas = Map.copyOf(fabricas);
    }

    @Override
    public Jwt decode(String token) {
        String emisor = emisorSinVerificar(token);
        Supplier<JwtDecoder> fabrica = fabricas.get(emisor);
        if (fabrica == null) {
            // El emisor no va en el mensaje: iría al cliente.
            throw new BadJwtException("El token no viene de un emisor aceptado por la plataforma");
        }
        return creados.computeIfAbsent(emisor, clave -> fabrica.get()).decode(token);
    }

    private static String emisorSinVerificar(String token) {
        try {
            String emisor = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            if (emisor == null || emisor.isBlank()) {
                throw new BadJwtException("El token no trae emisor");
            }
            return sinBarraFinal(emisor);
        } catch (ParseException malFormado) {
            throw new BadJwtException("El token no tiene formato JWT", malFormado);
        }
    }

    static String sinBarraFinal(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
