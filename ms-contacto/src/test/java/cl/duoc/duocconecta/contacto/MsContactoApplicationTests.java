package cl.duoc.duocconecta.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pruebas de ms-contacto: que el contexto arranque, que la seguridad esté puesta y que el
 * consentimiento funcione de punta a punta (pedir, aceptar y recién ahí ver los datos).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsContactoApplicationTests {

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
    @DisplayName("GET /api/v1/colaboraciones/recibidas sin token responde 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/colaboraciones/recibidas"))
                .andExpect(status().isUnauthorized());
    }

    /** El endpoint de salud queda abierto para que el monitoreo pueda consultarlo. */
    @Test
    @DisplayName("GET /actuator/health responde 200 sin token")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    /** Un dominio que no es de Duoc queda fuera, aunque el token traiga rol. */
    @Test
    @DisplayName("Un dominio no autorizado recibe 403")
    void dominioExternoRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/colaboraciones/recibidas").with(tokenDe("alguien@gmail.com")))
                .andExpect(status().isForbidden());
    }

    /** Pedirse contacto a uno mismo no tiene sentido: es la petición la que está mal, no el permiso. */
    @Test
    @DisplayName("Solicitar contacto a uno mismo responde 400")
    void noSePuedeSolicitarASiMismo() throws Exception {
        String yo = "oid-carla.soto@duocuc.cl";
        mockMvc.perform(post("/api/v1/colaboraciones")
                        .with(tokenDe("carla.soto@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitadoId\": \"" + yo + "\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Repetir la solicitud por el mismo proyecto es 409, no 403. */
    @Test
    @DisplayName("Repetir una solicitud por el mismo proyecto responde 409")
    void solicitudRepetidaResponde409() throws Exception {
        RequestPostProcessor quienPide = tokenDe("luis.rojas@duocuc.cl");
        String cuerpo = """
                {
                  "solicitadoId": "oid-marta.diaz@duocuc.cl",
                  "proyectoId": "11111111-1111-1111-1111-111111111111",
                  "mensaje": "Me sumo"
                }""";

        mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());
    }

    /**
     * A la misma persona se le puede pedir contacto por otro de sus proyectos.
     *
     * <p>Antes la unicidad era por par de personas, así que pedirle contacto a alguien una vez
     * bloqueaba todos sus demás proyectos.</p>
     */
    @Test
    @DisplayName("Se puede pedir contacto a la misma persona por otro proyecto")
    void otroProyectoDeLaMismaPersonaSePuedePedir() throws Exception {
        RequestPostProcessor quienPide = tokenDe("sofia.mena@duocuc.cl");
        String plantilla = """
                {
                  "solicitadoId": "oid-rodrigo.paz@duocuc.cl",
                  "proyectoId": "%s",
                  "mensaje": "Me interesa"
                }""";

        mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla.formatted("22222222-2222-2222-2222-222222222222")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plantilla.formatted("33333333-3333-3333-3333-333333333333")))
                .andExpect(status().isCreated());
    }

    /** Si ya colaboran en ese proyecto, no tiene sentido volver a pedirlo. */
    @Test
    @DisplayName("Pedir contacto por un proyecto ya aceptado responde 409")
    void proyectoYaAceptadoResponde409() throws Exception {
        RequestPostProcessor quienPide = tokenDe("ivan.soto@duocuc.cl");
        RequestPostProcessor quienRecibe = tokenDe("elena.mora@duocuc.cl");
        String cuerpo = """
                {
                  "solicitadoId": "oid-elena.mora@duocuc.cl",
                  "proyectoId": "44444444-4444-4444-4444-444444444444",
                  "mensaje": "Hola"
                }""";

        String creada = mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(patch("/api/v1/colaboraciones/" + JsonPath.read(creada, "$.id") + "/responder")
                        .with(quienRecibe)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\": true, \"correo\": \"elena.mora@duocuc.cl\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/colaboraciones").with(quienPide)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isConflict());
    }

    /**
     * El recorrido completo del consentimiento.
     *
     * <p>Una persona pide contacto ofreciendo los suyos, la solicitud queda PENDIENTE y sin nada
     * visible; la otra acepta eligiendo qué mostrar, y solo entonces <b>las dos partes</b> ven los
     * datos de la otra. Es exactamente la garantía que la plataforma promete, y en los dos
     * sentidos.</p>
     */
    @Test
    @DisplayName("Los datos de contacto aparecen solo después de que la otra persona acepta")
    void losDatosAparecenReciénAlAceptar() throws Exception {
        RequestPostProcessor solicitante = tokenDe("pedro.vera@duocuc.cl");
        RequestPostProcessor solicitado = tokenDe("ana.lagos@profesor.duoc.cl");

        String creada = mockMvc.perform(post("/api/v1/colaboraciones")
                        .with(solicitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "solicitadoId": "oid-ana.lagos@profesor.duoc.cl",
                                  "mensaje": "Me interesa tu proyecto",
                                  "contactoSolicitante": {
                                    "correo": "pedro.vera@duocuc.cl",
                                    "telefono": "912345678",
                                    "redes": "@pedrovera"
                                  }
                                }"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                // Mientras esté pendiente no sale ningún dato, ni siquiera el que ofreció quien pidió.
                .andExpect(jsonPath("$.contactoSolicitante.correo").doesNotExist())
                .andExpect(jsonPath("$.contactoSolicitado.correo").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String id = JsonPath.read(creada, "$.id");

        // A quien recibe le llega la solicitud en su bandeja.
        mockMvc.perform(get("/api/v1/colaboraciones/recibidas").with(solicitado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").exists());

        // Solo quien la recibió puede responderla.
        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/responder")
                        .with(solicitante)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\": true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/responder")
                        .with(solicitado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "aceptar": true,
                                  "correo": "ana.lagos@profesor.duoc.cl",
                                  "redes": "@analagos"
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADA"));

        // Quien pidió ve los datos de ella. El teléfono no viene porque no lo compartió, que es
        // distinto de no tenerlo.
        mockMvc.perform(get("/api/v1/colaboraciones/enviadas").with(solicitante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contactoSolicitado.correo")
                        .value("ana.lagos@profesor.duoc.cl"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contactoSolicitado.redes")
                        .value("@analagos"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contactoSolicitado.telefono[0]")
                        .doesNotExist());

        // Y ella ve los de él, que es lo que antes no ocurría: el intercambio era en un solo
        // sentido y quien aceptaba se quedaba sin forma de contactar a quien le había escrito.
        mockMvc.perform(get("/api/v1/colaboraciones/recibidas").with(solicitado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contactoSolicitante.correo")
                        .value("pedro.vera@duocuc.cl"))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].contactoSolicitante.telefono")
                        .value("912345678"));

        // Una solicitud ya respondida no se responde de nuevo. Es 409 y no 403: quien responde
        // sí tiene permiso, lo que ya no está disponible es el estado pendiente.
        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/responder")
                        .with(solicitado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aceptar\": false}"))
                .andExpect(status().isConflict());
    }

    /**
     * Arma un token de prueba con los claims que emite Azure AD.
     *
     * <p>La authority se pone a mano porque el postprocesador {@code jwt()} no pasa por el
     * conversor de roles. El servicio igual vuelve a validar el dominio del correo, así que un
     * dominio externo sigue quedando rechazado aunque traiga la authority.</p>
     */
    private static RequestPostProcessor tokenDe(String correo) {
        return jwt()
                .jwt(token -> token
                        .claim("oid", "oid-" + correo)
                        .claim("email", correo)
                        .claim("name", correo))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        "ROLE_ESTUDIANTE"));
    }
}
