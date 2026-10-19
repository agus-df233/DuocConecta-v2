package cl.duoc.duocconecta.bff;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.bff.dto.Autor;
import cl.duoc.duocconecta.bff.dto.Conteo;
import cl.duoc.duocconecta.bff.dto.EstadisticasProyectos;
import cl.duoc.duocconecta.bff.dto.EstadisticasUsuarios;
import cl.duoc.duocconecta.bff.dto.PerfilUsuario;
import cl.duoc.duocconecta.bff.service.ClienteProyectos;
import cl.duoc.duocconecta.bff.service.ClienteUsuarios;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** El dashboard de administración y los roles que mi-perfil le entrega al frontend. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministracionBffTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteUsuarios clienteUsuarios;

    @MockitoBean
    private ClienteProyectos clienteProyectos;

    private static RequestPostProcessor token(String correo, String... roles) {
        return jwt().jwt(t -> t.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r)).toList());
    }

    @Test
    @DisplayName("El resumen exige ADMINISTRADOR: 401 sin token y 403 sin el rol")
    void resumenSoloAdministrador() throws Exception {
        mockMvc.perform(get("/api/v1/bff/admin/resumen")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bff/admin/resumen").with(token("ana@duocuc.cl", "ROLE_ESTUDIANTE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El resumen cuenta por carrera, suma los proyectos abiertos y no reenvía los oid de las dueñas")
    void resumenPorCarrera() throws Exception {
        when(clienteUsuarios.estadisticas()).thenReturn(new EstadisticasUsuarios(3, 2,
                List.of(new Conteo("ESTUDIANTE", 3)), List.of(new Conteo("Informática", 2)), List.of()));
        when(clienteProyectos.estadisticas()).thenReturn(new EstadisticasProyectos(6, 4, 1, 2, 9,
                List.of(new Conteo("PUBLICO", 5)), List.of(), List.of(),
                List.of(new Conteo("oid-a", 3), new Conteo("oid-b", 2), new Conteo("oid-oculta", 1)),
                // Dos proyectos abiertos, ya contados por la carrera que eligió el administrador.
                2, List.of(new Conteo("Informática", 2))));
        when(clienteUsuarios.buscarAutorPorOid("oid-a")).thenReturn(new Autor("A", "Informática", "Viña del Mar"));
        when(clienteUsuarios.buscarAutorPorOid("oid-b")).thenReturn(new Autor("B", "Informática", "Viña del Mar"));
        when(clienteUsuarios.buscarAutorPorOid("oid-oculta")).thenReturn(Autor.DESCONOCIDO);

        mockMvc.perform(get("/api/v1/bff/admin/resumen")
                        .with(token("admin@duocuc.cl", "ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comunidad.total").value(3))
                .andExpect(jsonPath("$.vitrina.ocultosPorModeracion").value(1))
                .andExpect(jsonPath("$.vitrina.porCarrera[0].clave").value("Informática"))
                .andExpect(jsonPath("$.vitrina.porCarrera[0].cantidad").value(7))
                .andExpect(jsonPath("$.vitrina.porCarrera[1].clave").value("Perfil oculto o sin carrera"))
                .andExpect(jsonPath("$..porPropietario").doesNotExist());
    }

    @Test
    @DisplayName("mi-perfil devuelve los roles efectivos, incluido ADMINISTRADOR si se sumó")
    void miPerfilConRoles() throws Exception {
        when(clienteUsuarios.obtenerPerfilPropio()).thenReturn(new PerfilUsuario(
                "id-admin", "Admin", "admin@duocuc.cl", "ESTUDIANTE", null, null, null, true, null, List.of()));
        when(clienteUsuarios.obtenerRedesPropias()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bff/mi-perfil")
                        .with(token("admin@duocuc.cl", "ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.roles[1]").value("ADMINISTRADOR"));
    }
}
