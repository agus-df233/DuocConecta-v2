package cl.duoc.duocconecta.usuarios;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Las cifras del dashboard: solo para ADMINISTRADOR y sin datos personales. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstadisticasUsuariosTests {

    private static final String RUTA = "/api/v1/usuarios/admin/estadisticas";

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor token(String correo, String... roles) {
        return jwt().jwt(t -> t.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r)).toList());
    }

    @Test
    @DisplayName("Sin token: 401")
    void sinToken() throws Exception {
        mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un estudiante sin rol de administrador: 403")
    void sinAdministrador() throws Exception {
        mockMvc.perform(get(RUTA).with(token("ana@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El administrador ve las cifras, y quien entra por primera vez ya cuenta")
    void administradorVeLasCifras() throws Exception {
        // El primer GET /me crea el perfil: las cifras deben incluirlo.
        mockMvc.perform(get("/api/v1/usuarios/me").with(token("nuevo.conteo@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isOk());

        mockMvc.perform(get(RUTA).with(token("admin@duocuc.cl", "ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.porRol[?(@.clave == 'ESTUDIANTE')].cantidad").isNotEmpty())
                .andExpect(jsonPath("$.porCarrera[?(@.clave == 'Sin completar')]").isNotEmpty())
                .andExpect(jsonPath("$..correo").doesNotExist())
                .andExpect(jsonPath("$..telefono").doesNotExist());
    }
}
