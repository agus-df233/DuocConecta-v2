package cl.duoc.duocconecta.comun.seguridad;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lee los datos del usuario desde el JWT.
 *
 * <p>Azure AD emite claims distintos según el tipo de cuenta, así que se prueba una cadena de
 * alternativas configurable. Nadie más necesita saber en qué claim viene cada dato.</p>
 */
public class ClaimsDelToken {

    private final PropiedadesSeguridad propiedades;

    public ClaimsDelToken(PropiedadesSeguridad propiedades) {
        this.propiedades = propiedades;
    }

    /** El claim {@code oid}: nunca cambia, a diferencia del correo. Si falta, se usa {@code sub}. */
    public Optional<String> oid(Jwt token) {
        return primerClaimConTexto(token, List.of("oid", "sub"));
    }

    /** Busca en los claims de {@code duocconecta.seguridad.claims-correo}, en orden. */
    public Optional<String> correo(Jwt token) {
        return primerClaimConTexto(token, propiedades.getClaimsCorreo())
                .map(correo -> correo.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** Si el token no trae nombre, se usa la parte local del correo. */
    public String nombre(Jwt token) {
        return primerClaimConTexto(token, propiedades.getClaimsNombre())
                .orElseGet(() -> correo(token)
                        .map(correo -> correo.substring(0, correo.lastIndexOf('@')))
                        .orElse("Usuario sin nombre"));
    }

    /** Puede venir vacío; entonces el rol sale del dominio del correo. */
    public List<String> rolesDelToken(Jwt token) {
        Object valor = token.getClaim(propiedades.getClaimRoles());
        if (valor instanceof Collection<?> coleccion) {
            return coleccion.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(rol -> !rol.isBlank())
                    .toList();
        }
        // Algunos tenants emiten un único rol como texto plano en vez de una lista.
        if (valor instanceof String texto && !texto.isBlank()) {
            return List.of(texto);
        }
        return List.of();
    }

    /**
     * Devuelve el correo del token o falla si no está.
     *
     * @throws CorreoNoPresenteException si ningún claim configurado trae el correo
     */
    public String correoObligatorio(Jwt token) {
        return correo(token).orElseThrow(
                () -> new CorreoNoPresenteException(propiedades.getClaimsCorreo()));
    }

    /**
     * Recorre una lista de claims y devuelve el primero que tenga texto no vacío.
     */
    private Optional<String> primerClaimConTexto(Jwt token, List<String> nombresDeClaim) {
        if (nombresDeClaim == null) {
            return Optional.empty();
        }
        for (String nombre : nombresDeClaim) {
            String valor = token.getClaimAsString(nombre);
            if (valor != null && !valor.isBlank()) {
                return Optional.of(valor);
            }
        }
        return Optional.empty();
    }
}
