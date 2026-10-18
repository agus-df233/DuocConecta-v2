package cl.duoc.duocconecta.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Dos emisores: Entra ID (Microsoft) no emite email_verified, así que no se le exige; Cognito
 * (Google) sí, y un correo sin verificar no recibe rol.
 */
class CorreoVerificadoTest {

    private static final String ENTRA = "https://login.microsoftonline.com/tenant/v2.0";
    private static final String COGNITO = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_pool";

    private static List<String> authorities(String emisor, Object emailVerificado, String claimRoles, List<String> roles) {
        PropiedadesSeguridad propiedades = new PropiedadesSeguridad();
        propiedades.setDominios(Map.of("duocuc.cl", Rol.ESTUDIANTE));
        propiedades.setClaimsCorreo(List.of("email", "preferred_username"));
        propiedades.setClaimsRoles(List.of("roles", "cognito:groups"));
        propiedades.setEmisoresAdicionales(List.of(
                new PropiedadesSeguridad.Emisor(COGNITO, "app-client", true),
                new PropiedadesSeguridad.Emisor("", "", true)));      // vacía: se ignora
        ConversorRolesJwt conversor = new ConversorRolesJwt(
                new ClaimsDelToken(propiedades), new ResolvedorRol(propiedades), propiedades);
        Jwt.Builder token = Jwt.withTokenValue("t").header("alg", "none")
                .issuer(emisor).claim("sub", "sub-ana").claim("email", "ana@duocuc.cl");
        if (emailVerificado != null) {
            token.claim("email_verified", emailVerificado);
        }
        if (claimRoles != null) {
            token.claim(claimRoles, roles);
        }
        return conversor.convert(token.build()).getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("Entra ID no trae email_verified: igual recibe su rol")
    void entraSinEmailVerified() {
        assertThat(authorities(ENTRA, null, null, null)).containsExactly("ROLE_ESTUDIANTE");
    }

    @Test
    @DisplayName("Cognito con correo verificado (booleano o texto): recibe su rol")
    void cognitoVerificado() {
        assertThat(authorities(COGNITO, true, null, null)).containsExactly("ROLE_ESTUDIANTE");
        assertThat(authorities(COGNITO, "true", null, null)).containsExactly("ROLE_ESTUDIANTE");
    }

    @Test
    @DisplayName("Cognito sin verificar o sin el claim: sin roles (403)")
    void cognitoSinVerificar() {
        assertThat(authorities(COGNITO, false, null, null)).isEmpty();
        assertThat(authorities(COGNITO, null, null, null)).isEmpty();
        assertThat(authorities(COGNITO + "/", false, null, null)).isEmpty();   // la barra final no lo salta
    }

    @Test
    @DisplayName("Se suman los App Roles de Entra ('roles') y los grupos de Cognito ('cognito:groups')")
    void rolesDeAmbosClaims() {
        assertThat(authorities(ENTRA, null, "roles", List.of("ADMINISTRADOR")))
                .containsExactly("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
        assertThat(authorities(COGNITO, true, "cognito:groups", List.of("ADMINISTRADOR")))
                .containsExactly("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR");
    }
}
