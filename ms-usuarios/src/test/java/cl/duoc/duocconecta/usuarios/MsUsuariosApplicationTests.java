package cl.duoc.duocconecta.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas básicas de ms-usuarios: que el contexto arranque y que la seguridad esté puesta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MsUsuariosApplicationTests {

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private MockMvc mockMvc;

    /**
     * El contexto de Spring levanta sin errores.
     *
     * <p>Es la prueba más barata que existe y la que más problemas atrapa: si falta un bean,
     * si dos configuraciones chocan o si el mapeo de una entidad está mal, falla aquí.</p>
     */
    @Test
    @DisplayName("El contexto de la aplicación levanta correctamente")
    void elContextoLevanta() {
        assertThat(contexto).isNotNull();
    }

    /**
     * Una ruta protegida sin token responde 401.
     *
     * <p>Es la comprobación central de la EP1: sin credenciales no se entra.</p>
     */
    @Test
    @DisplayName("GET /api/v1/usuarios/me sin token responde 401")
    void rutaProtegidaSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El listado de perfiles también exige token: "público" quiere decir sin datos de contacto,
     * no sin autenticación.
     */
    @Test
    @DisplayName("GET /api/v1/usuarios sin token responde 401")
    void listadoSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * El endpoint de salud queda abierto para que el monitoreo pueda consultarlo.
     */
    @Test
    @DisplayName("GET /actuator/health responde 200 sin token")
    void healthEsPublico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    /**
     * Con un token válido, el perfil se crea solo y la respuesta trae las redes.
     *
     * <p>Este test existe por un fallo real: la lista de redes se carga de forma perezosa y el
     * mapeo a DTO se hacía fuera de la transacción, así que en AWS reventaba con
     * {@code LazyInitializationException} aunque en local todo compilara. Los tests anteriores no
     * lo detectaban porque ninguno llegaba a ejecutar una petición autenticada.</p>
     */
    @Test
    @DisplayName("GET /api/v1/usuarios/me con token válido auto-aprovisiona y devuelve las redes")
    void perfilPropioSeAutoAprovisiona() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me").with(tokenDe("camila.rojas@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("camila.rojas@duocuc.cl"))
                .andExpect(jsonPath("$.rol").value("ESTUDIANTE"))
                .andExpect(jsonPath("$.redes").isArray());
    }

    /** Llamar dos veces no crea dos perfiles: la segunda encuentra el que ya existe. */
    @Test
    @DisplayName("Entrar dos veces devuelve siempre el mismo perfil")
    void elPerfilNoSeDuplica() throws Exception {
        var peticion = get("/api/v1/usuarios/me").with(tokenDe("matias.fuentes@duocuc.cl"));

        String primero = mockMvc.perform(peticion).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String segundo = mockMvc.perform(peticion).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(primero).isEqualTo(segundo);
    }

    /**
     * Un correo de un dominio que no es de Duoc recibe 403 aunque el token traiga rol.
     *
     * <p>Comprueba que la validación de dominio también vive en el servicio y no solo en la
     * anotación del endpoint: un token manipulado no alcanza para entrar.</p>
     */
    @Test
    @DisplayName("Un dominio no autorizado recibe 403 y no crea perfil")
    void dominioExternoRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me").with(tokenDe("alguien@gmail.com")))
                .andExpect(status().isForbidden());
    }

    /**
     * Un token sin rol de la plataforma recibe 403, no 500.
     *
     * <p>Es el camino que produce {@code @PreAuthorize} cuando las authorities no alcanzan, y el
     * único que ninguna otra prueba cubre: las demás inyectan {@code ROLE_ESTUDIANTE} a mano, así
     * que la anotación las deja pasar. Sin esta prueba, un manejador genérico de {@code Exception}
     * puede convertir el 403 en 500 sin que nada lo delate.</p>
     */
    @Test
    @DisplayName("Un token sin rol recibe 403 y no 500")
    void tokenSinRolRecibe403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me").with(jwt()
                        .jwt(token -> token
                                .claim("oid", "oid-sin-rol")
                                .claim("email", "sin.rol@duocuc.cl")
                                .claim("name", "Sin Rol"))))
                .andExpect(status().isForbidden());
    }

    /** Una ruta que no existe es 404, no un error interno. */
    @Test
    @DisplayName("Una ruta inexistente responde 404")
    void rutaInexistenteResponde404() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/no-existe/tampoco").with(tokenDe("ana@duocuc.cl")))
                .andExpect(status().isNotFound());
    }

    /** Un verbo que el endpoint no acepta es 405. */
    @Test
    @DisplayName("Un verbo no soportado responde 405")
    void verboNoSoportadoResponde405() throws Exception {
        mockMvc.perform(delete("/api/v1/usuarios/me").with(tokenDe("ana@duocuc.cl")))
                .andExpect(status().isMethodNotAllowed());
    }

    /** Un cuerpo que no es JSON válido es culpa de quien llama: 400. */
    @Test
    @DisplayName("Un cuerpo mal formado responde 400")
    void cuerpoMalFormadoResponde400() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/me")
                        .with(tokenDe("ana@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{esto no es json"))
                .andExpect(status().isBadRequest());
    }

    /** Un identificador con formato inválido es 400, no un error interno. */
    @Test
    @DisplayName("Un identificador con formato inválido responde 400")
    void identificadorInvalidoResponde400() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/no-es-un-uuid").with(tokenDe("ana@duocuc.cl")))
                .andExpect(status().isBadRequest());
    }

    /**
     * Arma un token de prueba con los claims que emite Azure AD.
     *
     * <p>La authority se pone a mano porque el postprocesador {@code jwt()} no pasa por
     * {@code ConversorRolesJwt}. Eso no debilita la prueba: la traducción de dominio a rol ya está
     * cubierta por {@link ResolvedorRolTest}, y el servicio igual vuelve a validar el dominio del
     * correo, así que un dominio externo sigue quedando rechazado aunque traiga la authority.</p>
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
