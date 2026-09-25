package cl.duoc.duocconecta.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Los roles se suman al del dominio y nunca rescatan un dominio no autorizado. */
class ConversorRolesJwtTest {

    private static List<String> authorities(ConversorRolesJwt conversor, Jwt token) {
        return conversor.convert(token).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
    }

    private static ConversorRolesJwt conversor(List<String> administradores) {
        PropiedadesSeguridad propiedades = new PropiedadesSeguridad();
        propiedades.setDominios(Map.of(
                "duocuc.cl", Rol.ESTUDIANTE, "profesor.duoc.cl", Rol.PROFESOR, "duoc.cl", Rol.ACADEMICO));
        propiedades.setAdministradores(administradores);
        return new ConversorRolesJwt(
                new ClaimsDelToken(propiedades), new ResolvedorRol(propiedades), propiedades);
    }

    private static Jwt token(String correo, Object roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none")
                .claim("oid", "oid-" + correo).claim("email", correo);
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }

    @Test
    @DisplayName("Sin App Roles ni lista: solo el rol del dominio")
    void soloRolDeDominio() {
        assertThat(authorities(conversor(List.of()), token("ana@duocuc.cl", null)))
                .containsExactly("ROLE_ESTUDIANTE");
    }

    @Test
    @DisplayName("El App Role ADMINISTRADOR se suma al rol del dominio")
    void appRoleSeSuma() {
        assertThat(authorities(conversor(List.of()), token("ana@duocuc.cl", List.of("ADMINISTRADOR"))))
                .containsExactly("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }

    @Test
    @DisplayName("El valor del App Role no distingue mayúsculas")
    void appRoleSinDistinguirMayusculas() {
        assertThat(authorities(conversor(List.of()), token("ana@duocuc.cl", "administrador")))
                .contains("ROLE_ADMINISTRADOR");
    }

    @Test
    @DisplayName("Un App Role desconocido se ignora")
    void appRoleDesconocido() {
        assertThat(authorities(conversor(List.of()), token("ana@duocuc.cl", List.of("SUPERUSUARIO"))))
                .containsExactly("ROLE_ESTUDIANTE");
    }

    @Test
    @DisplayName("La lista de correos da ADMINISTRADOR, sin distinguir mayúsculas ni espacios")
    void listaDeCorreos() {
        assertThat(authorities(conversor(List.of(" Ana@DuocUC.cl ")), token("ana@duocuc.cl", null)))
                .containsExactly("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }

    @Test
    @DisplayName("Con la lista vacía nadie es administrador")
    void listaVaciaNoDaAdministrador() {
        assertThat(authorities(conversor(List.of("")), token("ana@duocuc.cl", null)))
                .doesNotContain("ROLE_ADMINISTRADOR");
    }

    @Test
    @DisplayName("Un dominio no autorizado queda sin roles, aunque traiga App Role o esté en la lista")
    void dominioNoAutorizadoNoSeRescata() {
        ConversorRolesJwt conversor = conversor(List.of("alguien@gmail.com"));
        assertThat(authorities(conversor, token("alguien@gmail.com", List.of("ADMINISTRADOR", "PROFESOR"))))
                .isEmpty();
    }
}
