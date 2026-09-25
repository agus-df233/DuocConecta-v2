package cl.duoc.duocconecta.proyectos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas básicas de ms-proyectos: que el contexto arranque, que la seguridad esté puesta
 * y que la vitrina responda a una petición autenticada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsProyectosApplicationTests {

    /** Cuerpo reutilizado por varias pruebas; lleva herramientas que no son de desarrollo. */
    private static final String PROYECTO_DE_PRUEBA = """
            {
              "nombre": "Sistema de reservas",
              "resumen": "Reserva de salas de estudio por sede.",
              "descripcion": "Proyecto de prueba",
              "herramientas": ["Figma", "Excel"],
              "estado": "BUSCANDO_EQUIPO",
              "visibilidad": "PUBLICO"
            }""";

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    /** El contexto levanta sin errores: atrapa beans faltantes y mapeos de entidad mal hechos. */
    @Test
    @DisplayName("El contexto de la aplicación levanta correctamente")
    void elContextoLevanta() {
        assertThat(contexto).isNotNull();
    }

    /** Sin credenciales no se entra: es la comprobación central de la evaluación. */
    @Test
    @DisplayName("GET /api/v1/proyectos sin token responde 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/proyectos")).andExpect(status().isUnauthorized());
    }

    /** El endpoint de salud queda abierto para que el monitoreo pueda consultarlo. */
    @Test
    @DisplayName("GET /actuator/health responde 200 sin token")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** Con un token válido la vitrina responde, aunque todavía no haya proyectos. */
    @Test
    @DisplayName("GET /api/v1/proyectos con token válido devuelve el listado")
    void conTokenDevuelveElListado() throws Exception {
        mockMvc.perform(get("/api/v1/proyectos").with(tokenDe("camila.rojas@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Publicar un proyecto y volver a listarlo lo encuentra.
     *
     * <p>Cubre el recorrido completo: el identificador de quien publica sale del token, se guarda
     * como propietario, y el listado lo devuelve porque es público.</p>
     */
    @Test
    @DisplayName("Un proyecto publicado aparece en la vitrina")
    void elProyectoPublicadoAparece() throws Exception {
        mockMvc.perform(post("/api/v1/proyectos")
                        .with(tokenDe("matias.fuentes@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content(PROYECTO_DE_PRUEBA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Sistema de reservas"))
                .andExpect(jsonPath("$.visibilidad").value("PUBLICO"))
                .andExpect(jsonPath("$.herramientas[0]").value("Figma"))
                .andExpect(jsonPath("$.cantidadComentarios").value(0));

        mockMvc.perform(get("/api/v1/proyectos").with(tokenDe("matias.fuentes@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre == 'Sistema de reservas')]").exists());
    }

    /** Sin resumen no se publica: es lo único que se lee en la tarjeta de la vitrina. */
    @Test
    @DisplayName("Publicar sin resumen responde 400")
    void sinResumenNoSePublica() throws Exception {
        String cuerpo = """
                {
                  "nombre": "Proyecto sin resumen",
                  "estado": "TERMINADO",
                  "visibilidad": "PUBLICO"
                }""";

        mockMvc.perform(post("/api/v1/proyectos")
                        .with(tokenDe("matias.fuentes@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    /**
     * El hilo de comentarios funciona de punta a punta.
     *
     * <p>Se publica un proyecto, se comenta, el comentario aparece en el hilo y el contador de la
     * tarjeta sube. Comentar no comparte datos de contacto: eso es lo que lo distingue de pedir
     * colaboración.</p>
     */
    @Test
    @DisplayName("Un comentario aparece en el hilo y suma al contador")
    void elComentarioApareceEnElHilo() throws Exception {
        String creado = mockMvc.perform(post("/api/v1/proyectos")
                        .with(tokenDe("valentina.diaz@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content(PROYECTO_DE_PRUEBA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(creado, "$.id");

        mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios")
                        .with(tokenDe("ana.lagos@profesor.duoc.cl"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\": \"Muy buena idea, sumen accesibilidad\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.texto").value("Muy buena idea, sumen accesibilidad"));

        mockMvc.perform(get("/api/v1/proyectos/" + id + "/comentarios")
                        .with(tokenDe("valentina.diaz@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/proyectos/" + id).with(tokenDe("valentina.diaz@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadComentarios").value(1));
    }

    /** Un comentario vacío no se publica. */
    @Test
    @DisplayName("Comentar con texto vacío responde 400")
    void comentarioVacioResponde400() throws Exception {
        String creado = mockMvc.perform(post("/api/v1/proyectos")
                        .with(tokenDe("valentina.diaz@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content(PROYECTO_DE_PRUEBA))
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(creado, "$.id");

        mockMvc.perform(post("/api/v1/proyectos/" + id + "/comentarios")
                        .with(tokenDe("valentina.diaz@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }

    /** Un dominio que no es de Duoc queda fuera, aunque el token traiga rol. */
    @Test
    @DisplayName("Un dominio no autorizado recibe 403")
    void dominioExternoRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/proyectos").with(tokenDe("alguien@gmail.com")))
                .andExpect(status().isForbidden());
    }

    /**
     * Arma un token de prueba con los claims que emite Azure AD.
     *
     * <p>La authority se pone a mano porque el postprocesador {@code jwt()} no pasa por el
     * conversor de roles. El servicio igual vuelve a validar el dominio del correo, así que un
     * dominio externo sigue quedando rechazado aunque traiga la authority.</p>
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor tokenDe(String correo) {
        return jwt()
                .jwt(token -> token
                        .claim("oid", "oid-" + correo)
                        .claim("email", correo)
                        .claim("name", correo))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_ESTUDIANTE"));
    }
}
