package cl.duoc.duocconecta.comun.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Punto único para obtener al usuario autenticado.
 *
 * <p>La identidad sale siempre del token, nunca de la petición: si el cliente pudiera mandar su
 * propio {@code oid}, cualquiera editaría el perfil ajeno cambiando un valor en la URL.</p>
 */
public class UsuarioActual {

    private final ClaimsDelToken claimsDelToken;
    private final ResolvedorRol resolvedorRol;

    public UsuarioActual(ClaimsDelToken claimsDelToken, ResolvedorRol resolvedorRol) {
        this.claimsDelToken = claimsDelToken;
        this.resolvedorRol = resolvedorRol;
    }

    /**
     * @throws CorreoNoPresenteException si el token no trae correo
     * @throws DominioNoPermitidoException si el dominio no está autorizado
     */
    public IdentidadUsuario obtener() {
        Jwt token = tokenActual();

        String correo = claimsDelToken.correoObligatorio(token);
        String dominio = resolvedorRol.extraerDominio(correo)
                .orElseThrow(() -> new DominioNoPermitidoException(correo));

        Rol rol = resolvedorRol.resolverPorDominio(dominio)
                .orElseThrow(() -> new DominioNoPermitidoException(dominio));

        String oid = claimsDelToken.oid(token)
                .orElseThrow(() -> new IllegalStateException(
                        "El token no trae el claim 'oid' ni 'sub'; no se puede identificar al usuario."));

        return new IdentidadUsuario(oid, correo, claimsDelToken.nombre(token), rol);
    }

    /** El token crudo de la petición en curso. */
    public Jwt tokenActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        throw new IllegalStateException(
                "No hay un token JWT en el contexto de seguridad para la petición en curso.");
    }

    /**
     * El token en curso, listo para la cabecera Authorization.
     *
     * <p>El BFF reenvía el token del usuario a los microservicios: nunca usa credenciales propias,
     * así cada servicio decide con la identidad real de quien pidió.</p>
     */
    public String cabeceraAuthorization() {
        return "Bearer " + tokenActual().getTokenValue();
    }

    /** Datos del usuario, ya extraídos y validados desde el token. */
    public record IdentidadUsuario(String oid, String correo, String nombre, Rol rol) {
    }
}
