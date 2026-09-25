package cl.duoc.duocconecta.comun.seguridad;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Convierte el JWT en una autenticación de Spring, con los roles como authorities.
 *
 * <p>Los roles se suman: el del dominio del correo siempre, más los App Roles conocidos, más
 * {@code ADMINISTRADOR} si el correo está en la lista. Así un administrador no pierde su rol de
 * dominio. Un dominio no autorizado queda sin authorities: nada lo rescata.</p>
 */
public class ConversorRolesJwt implements Converter<Jwt, AbstractAuthenticationToken> {

    private final ClaimsDelToken claimsDelToken;
    private final ResolvedorRol resolvedorRol;
    private final Set<String> administradores;

    public ConversorRolesJwt(ClaimsDelToken claimsDelToken, ResolvedorRol resolvedorRol,
                             PropiedadesSeguridad propiedades) {
        this.claimsDelToken = claimsDelToken;
        this.resolvedorRol = resolvedorRol;
        this.administradores = propiedades.getAdministradores().stream()
                .filter(correo -> correo != null && !correo.isBlank())
                .map(ConversorRolesJwt::normalizar)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** El principal se nombra con el correo, para que los registros se lean. */
    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        String nombrePrincipal = claimsDelToken.correo(token)
                .or(() -> claimsDelToken.oid(token))
                .orElse(token.getSubject());

        return new JwtAuthenticationToken(token, calcularAuthorities(token), nombrePrincipal);
    }

    private List<GrantedAuthority> calcularAuthorities(Jwt token) {
        Optional<String> correo = claimsDelToken.correo(token);
        Optional<Rol> rolDeDominio = correo.flatMap(resolvedorRol::resolverPorCorreo);
        if (rolDeDominio.isEmpty()) {
            return List.of();
        }

        Set<Rol> roles = new LinkedHashSet<>();
        roles.add(rolDeDominio.get());
        claimsDelToken.rolesDelToken(token).stream()
                .map(ConversorRolesJwt::aRolConocido)
                .flatMap(Optional::stream)
                .forEach(roles::add);
        if (administradores.contains(normalizar(correo.get()))) {
            roles.add(Rol.ADMINISTRADOR);
        }

        return roles.stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority(rol.comoAuthority()))
                .toList();
    }

    /** Se ignora todo rol que no exista aquí: uno suelto en Azure AD no debe dar permisos. */
    private static Optional<Rol> aRolConocido(String nombreRol) {
        try {
            return Optional.of(Rol.valueOf(nombreRol.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException noEsUnRolDeLaPlataforma) {
            return Optional.empty();
        }
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
