package cl.duoc.duocconecta.bff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.bff.service.ClienteContacto;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

/** El BFF reenvía el retiro a ms-contacto y respeta su respuesta, incluido el 409. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetiroColaboracionBffTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClienteContacto clienteContacto;

    @Test
    @DisplayName("Sin token: 401 y no se llama a ms-contacto")
    void sinToken() throws Exception {
        mockMvc.perform(patch("/api/v1/bff/colaboraciones/" + UUID.randomUUID() + "/retirar"))
                .andExpect(status().isUnauthorized());
        verify(clienteContacto, never()).retirar(any());
    }

    @Test
    @DisplayName("Una solicitud ya respondida: el 409 de ms-contacto llega igual, con su motivo")
    void conflictoSeRespeta() throws Exception {
        UUID id = UUID.randomUUID();
        String cuerpo = "{\"detail\":\"Esta solicitud ya fue respondida y no se puede retirar\"}";
        when(clienteContacto.retirar(id)).thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict",
                HttpHeaders.EMPTY, cuerpo.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        mockMvc.perform(patch("/api/v1/bff/colaboraciones/" + id + "/retirar")
                        .with(jwt().jwt(t -> t.claim("oid", "oid-x").claim("email", "x@duocuc.cl").claim("name", "X"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Esta solicitud ya fue respondida y no se puede retirar"));
        verify(clienteContacto).retirar(id);
    }
}
