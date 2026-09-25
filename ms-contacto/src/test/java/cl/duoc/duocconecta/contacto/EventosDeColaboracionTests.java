package cl.duoc.duocconecta.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.PublicadorEventos;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Pedir y responder una colaboración publica su evento, sin datos de contacto. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventosDeColaboracionTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicadorEventos publicador;

    @Test
    @DisplayName("Solicitar y aceptar publican colaboracion.solicitada y colaboracion.aceptada")
    void solicitarYAceptarPublicanSusEventos() throws Exception {
        String cuerpo = """
                {
                  "solicitadoId": "oid-paula.vera@duocuc.cl",
                  "proyectoId": "55555555-5555-5555-5555-555555555555",
                  "mensaje": "Hola",
                  "contactoSolicitante": {"correo": "tomas.rey@duocuc.cl", "telefono": "+56911112222"}
                }""";
        String respuesta = mockMvc.perform(post("/api/v1/colaboraciones").with(tokenDe("tomas.rey@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(respuesta, "$.id");

        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/responder").with(tokenDe("paula.vera@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": true}"))
                .andExpect(status().isOk());

        ArgumentCaptor<EventoDuocConecta> eventos = ArgumentCaptor.forClass(EventoDuocConecta.class);
        verify(publicador, times(2)).publicar(eventos.capture());

        EventoDuocConecta solicitada = eventos.getAllValues().get(0);
        assertThat(solicitada.tipo()).isEqualTo(TiposDeEvento.COLABORACION_SOLICITADA);
        assertThat(solicitada.actorId()).isEqualTo("oid-tomas.rey@duocuc.cl");
        assertThat(solicitada.destinatarioId()).isEqualTo("oid-paula.vera@duocuc.cl");
        assertThat(solicitada.recursoId()).isEqualTo(id);
        assertThat(solicitada.proyectoId()).isEqualTo("55555555-5555-5555-5555-555555555555");

        EventoDuocConecta aceptada = eventos.getAllValues().get(1);
        assertThat(aceptada.tipo()).isEqualTo(TiposDeEvento.COLABORACION_ACEPTADA);
        assertThat(aceptada.destinatarioId()).isEqualTo("oid-tomas.rey@duocuc.cl");

        // Regla dura: ningún campo de ningún evento es un dato de contacto.
        for (EventoDuocConecta evento : eventos.getAllValues()) {
            assertThat(java.util.List.of(evento.actorId(), evento.destinatarioId(), evento.recursoId(),
                            String.valueOf(evento.proyectoId()), String.valueOf(evento.detalle())))
                    .doesNotContain("tomas.rey@duocuc.cl", "+56911112222");
        }
    }

    @Test
    @DisplayName("Una solicitud rechazada por la regla de negocio no publica nada")
    void solicitudInvalidaNoPublica() throws Exception {
        mockMvc.perform(post("/api/v1/colaboraciones").with(tokenDe("ana.luz@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"solicitadoId\": \"oid-ana.luz@duocuc.cl\"}"))
                .andExpect(status().isBadRequest());
        verify(publicador, never()).publicar(any());
    }

    private static RequestPostProcessor tokenDe(String correo) {
        return jwt().jwt(token -> token.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"));
    }
}
