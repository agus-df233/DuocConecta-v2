package cl.duoc.duocconecta.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Una cuenta externa (Google) solo entra si su correo exacto está en la lista. */
class CorreosPermitidosTest {

    private static PropiedadesSeguridad propiedades(List<String> permitidos, List<String> administradores) {
        PropiedadesSeguridad propiedades = new PropiedadesSeguridad();
        propiedades.setDominios(Map.of(
                "duocuc.cl", Rol.ESTUDIANTE, "profesor.duoc.cl", Rol.PROFESOR, "duoc.cl", Rol.ACADEMICO));
        propiedades.setCorreosPermitidos(permitidos);
        propiedades.setAdministradores(administradores);
        return propiedades;
    }

    private static List<String> authorities(PropiedadesSeguridad propiedades, String correo) {
        ConversorRolesJwt conversor = new ConversorRolesJwt(
                new ClaimsDelToken(propiedades), new ResolvedorRol(propiedades), propiedades);
        Jwt token = Jwt.withTokenValue("t").header("alg", "none")
                .claim("oid", "oid-" + correo).claim("email", correo).build();
        return conversor.convert(token).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("Un Gmail de la lista recibe el rol indicado")
    void gmailDeLaListaRecibeSuRol() {
        ResolvedorRol resolvedor = new ResolvedorRol(propiedades(List.of("ana.google@gmail.com=ESTUDIANTE"), List.of()));
        assertThat(resolvedor.resolverPorCorreo("ana.google@gmail.com")).contains(Rol.ESTUDIANTE);
    }

    @Test
    @DisplayName("La lista no distingue mayúsculas ni espacios")
    void listaNormalizada() {
        ResolvedorRol resolvedor = new ResolvedorRol(propiedades(List.of(" Ana.Google@Gmail.com = profesor "), List.of()));
        assertThat(resolvedor.resolverPorCorreo("ana.google@gmail.com")).contains(Rol.PROFESOR);
    }

    @Test
    @DisplayName("Un Gmail fuera de la lista sigue sin rol: 403")
    void gmailFueraDeLaListaSigueSinRol() {
        assertThat(authorities(propiedades(List.of("ana.google@gmail.com=ESTUDIANTE"), List.of()), "otro@gmail.com"))
                .isEmpty();
    }

    @Test
    @DisplayName("La lista no altera la regla de dominio")
    void dominioInstitucionalIntacto() {
        ResolvedorRol resolvedor = new ResolvedorRol(propiedades(List.of("ana.google@gmail.com=ESTUDIANTE"), List.of()));
        assertThat(resolvedor.resolverPorCorreo("ignacio@profesor.duoc.cl")).contains(Rol.PROFESOR);
    }

    @Test
    @DisplayName("Un Gmail permitido también puede ser administrador por la lista de administradores")
    void gmailPermitidoPuedeSerAdministrador() {
        assertThat(authorities(propiedades(List.of("ana.google@gmail.com=ESTUDIANTE"), List.of("ana.google@gmail.com")),
                "ana.google@gmail.com"))
                .containsExactly("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }

    @Test
    @DisplayName("Una entrada mal escrita, un rol desconocido o ADMINISTRADOR detienen el arranque")
    void entradasInvalidasFallan() {
        assertThatThrownBy(() -> new ResolvedorRol(propiedades(List.of("ana.google@gmail.com"), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("correo=ROL");
        assertThatThrownBy(() -> new ResolvedorRol(propiedades(List.of("ana@gmail.com=JEFE"), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Rol desconocido");
        assertThatThrownBy(() -> new ResolvedorRol(propiedades(List.of("ana@gmail.com=ADMINISTRADOR"), List.of())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("administradores");
    }

    @Test
    @DisplayName("Entradas vacías se ignoran (la variable de entorno puede venir vacía)")
    void entradasVaciasSeIgnoran() {
        ResolvedorRol resolvedor = new ResolvedorRol(propiedades(List.of(""), List.of()));
        assertThat(resolvedor.resolverPorCorreo("ana@gmail.com")).isEmpty();
    }
}
