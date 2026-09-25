package cl.duoc.duocconecta.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.PublicadorEventos;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Moderación de la vitrina: solo ADMINISTRADOR, sin pisar la visibilidad que eligió la dueña. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModeracionTests {

    private static final String PROYECTO = """
            {
              "nombre": "Proyecto a moderar",
              "resumen": "Prueba de moderación.",
              "descripcion": "Prueba",
              "herramientas": ["Figma"],
              "estado": "EN_DESARROLLO",
              "visibilidad": "PUBLICO"
            }""";

    private static final String DUENA = "dueña.mod@duocuc.cl";
    private static final String TERCERO = "tercero.mod@duocuc.cl";
    private static final String ADMIN = "admin.mod@duocuc.cl";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicadorEventos publicador;

    private String crearProyecto() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/proyectos").with(estudiante(DUENA))
                        .contentType(MediaType.APPLICATION_JSON).content(PROYECTO))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    @Test
    @DisplayName("Sin rol ADMINISTRADOR, /admin responde 403; con el rol, 200")
    void soloAdministrador() throws Exception {
        mockMvc.perform(get("/api/v1/proyectos/admin").with(estudiante(TERCERO))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/proyectos/admin").with(administrador(ADMIN))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/proyectos/admin/comentarios").with(administrador(ADMIN))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El administrador conserva su rol de dominio: sigue usando la vitrina")
    void administradorUsaLaVitrina() throws Exception {
        mockMvc.perform(get("/api/v1/proyectos").with(administrador(ADMIN))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ocultar: sale de la vitrina, la dueña lo ve con el motivo y no puede revertirlo")
    void ocultarYMostrar() throws Exception {
        String id = crearProyecto();

        mockMvc.perform(patch("/api/v1/proyectos/admin/" + id + "/ocultar").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \" \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/v1/proyectos/admin/" + id + "/ocultar").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Contenido ajeno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oculto").value(true));

        mockMvc.perform(patch("/api/v1/proyectos/admin/" + id + "/ocultar").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\": \"Otra vez\"}"))
                .andExpect(status().isConflict());

        // Terceros: fuera de la vitrina, sin acceso al detalle ni a comentar.
        mockMvc.perform(get("/api/v1/proyectos").with(estudiante(TERCERO)))
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mockMvc.perform(get("/api/v1/proyectos/" + id).with(estudiante(TERCERO))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios").with(estudiante(TERCERO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"Hola\"}"))
                .andExpect(status().isForbidden());

        // La dueña lo ve con el motivo, y un PUT suyo no lo vuelve a mostrar.
        mockMvc.perform(get("/api/v1/proyectos/" + id).with(estudiante(DUENA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoOcultamiento").value("Contenido ajeno"));
        mockMvc.perform(put("/api/v1/proyectos/" + id).with(estudiante(DUENA))
                        .contentType(MediaType.APPLICATION_JSON).content(PROYECTO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oculto").value(true));

        mockMvc.perform(patch("/api/v1/proyectos/admin/" + id + "/mostrar").with(administrador(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oculto").value(false));
        mockMvc.perform(patch("/api/v1/proyectos/admin/" + id + "/mostrar").with(administrador(ADMIN)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/proyectos").with(estudiante(TERCERO)))
                .andExpect(jsonPath("$[*].id", hasItem(id)));

        List<EventoDuocConecta> eventos = eventosPublicados();
        EventoDuocConecta ocultado = eventos.stream()
                .filter(e -> e.tipo().equals(TiposDeEvento.PROYECTO_OCULTADO) && e.recursoId().equals(id))
                .findFirst().orElseThrow();
        assertThat(ocultado.destinatarioId()).isEqualTo("oid-" + DUENA);
        assertThat(ocultado.actorId()).isEqualTo("oid-" + ADMIN);
        assertThat(ocultado.detalle()).isEqualTo("Contenido ajeno");
        assertThat(eventos).anyMatch(e -> e.tipo().equals(TiposDeEvento.PROYECTO_RESTAURADO) && e.recursoId().equals(id));
    }

    @Test
    @DisplayName("Borrar un comentario y un proyecto: 204, y repetir da 404")
    void borrarComentarioYProyecto() throws Exception {
        String id = crearProyecto();
        String comentario = mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios").with(estudiante(TERCERO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"Spam\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String comentarioId = JsonPath.read(comentario, "$.id");

        mockMvc.perform(get("/api/v1/proyectos/admin/comentarios").param("proyectoId", id).with(administrador(ADMIN)))
                .andExpect(jsonPath("$[*].id", hasItem(comentarioId)));
        mockMvc.perform(delete("/api/v1/proyectos/admin/comentarios/" + comentarioId).with(administrador(ADMIN)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/proyectos/admin/comentarios/" + comentarioId).with(administrador(ADMIN)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/proyectos/admin/" + id).with(estudiante(TERCERO))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/proyectos/admin/" + id).with(administrador(ADMIN))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/proyectos/admin/" + id).with(administrador(ADMIN))).andExpect(status().isNotFound());

        List<EventoDuocConecta> eventos = eventosPublicados();
        assertThat(eventos).anyMatch(e -> e.tipo().equals(TiposDeEvento.PROYECTO_COMENTARIO_ELIMINADO)
                && e.destinatarioId().equals("oid-" + TERCERO));
        assertThat(eventos).anyMatch(e -> e.tipo().equals(TiposDeEvento.PROYECTO_ELIMINADO)
                && e.recursoId().equals(id) && e.destinatarioId().equals("oid-" + DUENA));
    }

    @Test
    @DisplayName("Publicar y comentar publican proyecto.publicado y proyecto.comentado")
    void eventosDeLaDuena() throws Exception {
        String id = crearProyecto();
        mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios").with(estudiante(TERCERO))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"Buen proyecto\"}"))
                .andExpect(status().isCreated());

        List<EventoDuocConecta> eventos = eventosPublicados();
        assertThat(eventos).anyMatch(e -> e.tipo().equals(TiposDeEvento.PROYECTO_PUBLICADO) && e.recursoId().equals(id));
        assertThat(eventos).anyMatch(e -> e.tipo().equals(TiposDeEvento.PROYECTO_COMENTADO)
                && id.equals(e.proyectoId()) && e.destinatarioId().equals("oid-" + DUENA));
    }

    private List<EventoDuocConecta> eventosPublicados() {
        ArgumentCaptor<EventoDuocConecta> captura = ArgumentCaptor.forClass(EventoDuocConecta.class);
        verify(publicador, atLeastOnce()).publicar(captura.capture());
        return captura.getAllValues();
    }

    private static RequestPostProcessor estudiante(String correo) {
        return token(correo, List.of("ROLE_ESTUDIANTE"));
    }

    /** El rol se suma al del dominio, como hace {@code ConversorRolesJwt}. */
    private static RequestPostProcessor administrador(String correo) {
        return token(correo, List.of("ROLE_ESTUDIANTE", "ROLE_ADMINISTRADOR"));
    }

    private static RequestPostProcessor token(String correo, List<String> roles) {
        return jwt().jwt(t -> t.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority(r)).toList());
    }
}
