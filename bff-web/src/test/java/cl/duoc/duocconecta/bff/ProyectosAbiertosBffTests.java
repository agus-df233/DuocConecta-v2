package cl.duoc.duocconecta.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.bff.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.bff.service.ClienteContacto;
import cl.duoc.duocconecta.bff.service.ClienteProyectos;
import cl.duoc.duocconecta.bff.service.ClienteUsuarios;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Proyectos abiertos en el BFF: se muestran como de DuocConecta y no reciben solicitudes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProyectosAbiertosBffTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteProyectos clienteProyectos;

    @MockitoBean
    private ClienteUsuarios clienteUsuarios;

    @MockitoBean
    private ClienteContacto clienteContacto;

    private static ProyectoRespuesta abierto(UUID id) {
        return new ProyectoRespuesta(id, "Huerto urbano", "Riego con sensores", null, null, "oid-admin", null,
                "Viña del Mar", List.of("Arduino"), "EN_DESARROLLO", "PUBLICO", List.of(), List.of(), 0,
                Instant.now(), false, null, true, "Ingeniería en Informática");
    }

    @Test
    @DisplayName("La vitrina muestra un proyecto abierto como de DuocConecta, con la carrera elegida")
    void vitrinaMuestraLaPlataforma() throws Exception {
        when(clienteProyectos.listar()).thenReturn(List.of(abierto(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/bff/vitrina").with(estudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].abierto").value(true))
                .andExpect(jsonPath("$[0].autor.nombre").value("DuocConecta"))
                .andExpect(jsonPath("$[0].autor.carrera").value("Ingeniería en Informática"))
                .andExpect(jsonPath("$[0].autor.sede").value("Viña del Mar"));
        // No se busca el perfil del administrador.
        verify(clienteUsuarios, never()).buscarAutorPorOid("oid-admin");
    }

    @Test
    @DisplayName("Pedir colaborar en un proyecto abierto: 400 con el motivo, y nunca llega a ms-contacto")
    void solicitudAProyectoAbiertoEsRechazada() throws Exception {
        UUID id = UUID.randomUUID();
        when(clienteProyectos.esAbierto(id)).thenReturn(true);

        mockMvc.perform(post("/api/v1/bff/colaboraciones").with(estudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitadoId\": \"oid-admin\", \"proyectoId\": \"" + id + "\", \"compartirTelefono\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Los proyectos abiertos se pueden comentar, pero no reciben solicitudes de colaboración."));
        verify(clienteContacto, never()).crear(any(), any());
    }

    private static RequestPostProcessor estudiante() {
        return jwt().jwt(t -> t.claim("oid", "oid-ana").claim("email", "ana@duocuc.cl").claim("name", "Ana"))
                .authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"));
    }
}
