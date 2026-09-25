package cl.duoc.duocconecta.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** El contexto levanta sin AWS, y la seguridad separa a la persona del administrador. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsNotificacionesApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("El contexto levanta sin AWS ni base externa")
    void elContextoLevanta() {
        assertThat(contexto).isNotNull();
    }

    @Test
    @DisplayName("Sin token, la bandeja responde 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Con token, la bandeja propia responde 200 y vacía")
    void bandejaPropiaVacia() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(token("lucia.paz@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/notificaciones/no-leidos").with(token("lucia.paz@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    @DisplayName("La administración exige el rol ADMINISTRADOR")
    void administracionSoloAdministrador() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones/admin/consumidores").with(token("lucia.paz@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/notificaciones/admin/consumidores/pausa").with(token("lucia.paz@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/notificaciones/admin/consumidores")
                        .with(token("admin@duocuc.cl", "ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Un dominio no autorizado recibe 403")
    void dominioExternoRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/notificaciones").with(token("alguien@gmail.com", "ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor token(String correo, String... roles) {
        List<GrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r)).toList();
        return jwt().jwt(t -> t.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(authorities);
    }
}
