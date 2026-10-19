package cl.duoc.duocconecta.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

/** Proyectos abiertos: los publica el administrador, se comentan y no tienen colaboradores. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProyectosAbiertosTests {

    private static final String ABIERTO = """
            {
              "nombre": "Huerto urbano en la sede",
              "resumen": "Riego automático con sensores de humedad.",
              "descripcion": "Proyecto de muestra para la vitrina.",
              "sede": "Viña del Mar",
              "carrera": "Ingeniería en Informática",
              "herramientas": ["Arduino", "Python"],
              "estado": "EN_DESARROLLO"
            }""";

    private static final String ADMIN = "admin.abiertos@duocuc.cl";
    private static final String ESTUDIANTE = "ana.abiertos@duocuc.cl";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicadorEventos publicador;

    private String crearAbierto() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/proyectos/admin/abiertos").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ABIERTO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.abierto").value(true))
                .andExpect(jsonPath("$.visibilidad").value("PUBLICO"))
                .andExpect(jsonPath("$.carrera").value("Ingeniería en Informática"))
                .andExpect(jsonPath("$.colaboradoresIds").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    @Test
    @DisplayName("El administrador lo publica y aparece en la vitrina de cualquier estudiante")
    void publicarYVerEnLaVitrina() throws Exception {
        String id = crearAbierto();
        mockMvc.perform(get("/api/v1/proyectos").with(estudiante(ESTUDIANTE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)));
    }

    @Test
    @DisplayName("Sin rol ADMINISTRADOR: 403; sin carrera: 400 con el motivo")
    void soloAdministradorYConCarrera() throws Exception {
        mockMvc.perform(post("/api/v1/proyectos/admin/abiertos").with(estudiante(ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON).content(ABIERTO))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/proyectos/admin/abiertos").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ABIERTO.replace("\"carrera\": \"Ingeniería en Informática\",", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Comentar uno abierto publica el evento sin destinatario: nadie recibe aviso")
    void comentarNoAvisa() throws Exception {
        String id = crearAbierto();
        mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios").with(estudiante(ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"¡Buena idea!\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<EventoDuocConecta> eventos = ArgumentCaptor.forClass(EventoDuocConecta.class);
        verify(publicador, atLeastOnce()).publicar(eventos.capture());
        EventoDuocConecta comentado = eventos.getAllValues().stream()
                .filter(e -> TiposDeEvento.PROYECTO_COMENTADO.equals(e.tipo()) && id.equals(e.proyectoId()))
                .findFirst().orElseThrow();
        assertThat(comentado.destinatarioId()).isNull();
    }

    @Test
    @DisplayName("No se edita con el PUT de la dueña ni admite colaboradores: 409; se edita desde admin")
    void reglasDeEdicion() throws Exception {
        String id = crearAbierto();
        mockMvc.perform(put("/api/v1/proyectos/" + id).with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre": "x", "resumen": "x", "estado": "TERMINADO", "visibilidad": "PRIVADO"}"""))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/proyectos/" + id + "/colaboradores").with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"colaboradorId\": \"oid-otra\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/proyectos/admin/abiertos/" + id).with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ABIERTO.replace("Ingeniería en Informática", "Diseño Gráfico")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carrera").value("Diseño Gráfico"))
                .andExpect(jsonPath("$.abierto").value(true));
    }

    @Test
    @DisplayName("Un proyecto de la comunidad no se edita por la ruta de abiertos: 409")
    void comunidadNoSeEditaComoAbierto() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/proyectos").with(estudiante(ESTUDIANTE))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"nombre": "Mío", "resumen": "De la comunidad", "estado": "EN_DESARROLLO", "visibilidad": "PUBLICO"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");
        mockMvc.perform(put("/api/v1/proyectos/admin/abiertos/" + id).with(administrador(ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(ABIERTO))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Estadísticas: los abiertos se cuentan aparte, por la carrera elegida, y no por su dueño")
    void estadisticasSeparanLosAbiertos() throws Exception {
        crearAbierto();
        mockMvc.perform(get("/api/v1/proyectos/admin/estadisticas").with(administrador(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abiertos").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.porCarreraAbiertos[?(@.clave == 'Ingeniería en Informática')]").isNotEmpty())
                .andExpect(jsonPath("$.porPropietario[?(@.clave == 'oid-" + ADMIN + "')]").isEmpty());
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
