package cl.duoc.duocconecta.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.PublicadorEventos;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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

/** Retirar una solicitud: solo quien la pidió, solo pendiente, y libera el cupo del proyecto. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RetiroDeSolicitudesTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PublicadorEventos publicador;

    @Test
    @DisplayName("Quien la pidió la retira: queda RETIRADA, publica el evento y puede volver a pedir")
    void retirarYVolverAPedir() throws Exception {
        String proyecto = UUID.randomUUID().toString();
        String id = pedir("rosa.diaz@duocuc.cl", "oid-ivan.soto@duocuc.cl", proyecto);

        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/retirar").with(tokenDe("rosa.diaz@duocuc.cl")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RETIRADA"));

        ArgumentCaptor<EventoDuocConecta> eventos = ArgumentCaptor.forClass(EventoDuocConecta.class);
        verify(publicador, atLeastOnce()).publicar(eventos.capture());
        EventoDuocConecta retirada = eventos.getValue();
        assertThat(retirada.tipo()).isEqualTo(TiposDeEvento.COLABORACION_RETIRADA);
        assertThat(retirada.actorId()).isEqualTo("oid-rosa.diaz@duocuc.cl");
        assertThat(retirada.destinatarioId()).isEqualTo("oid-ivan.soto@duocuc.cl");
        assertThat(retirada.recursoId()).isEqualTo(id);

        // El cupo del proyecto quedó libre: pedir de nuevo no choca con la retirada.
        pedir("rosa.diaz@duocuc.cl", "oid-ivan.soto@duocuc.cl", proyecto);
    }

    @Test
    @DisplayName("Otra persona no la puede retirar (403), ni siquiera quien la recibió")
    void soloQuienLaPidio() throws Exception {
        String id = pedir("hugo.paz@duocuc.cl", "oid-eva.rios@duocuc.cl", UUID.randomUUID().toString());
        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/retirar").with(tokenDe("eva.rios@duocuc.cl")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Una solicitud ya respondida no se retira: 409, no 403")
    void respondidaEsConflicto() throws Exception {
        String id = pedir("luz.mar@duocuc.cl", "oid-leo.paz@duocuc.cl", UUID.randomUUID().toString());
        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/responder").with(tokenDe("leo.paz@duocuc.cl"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceptar\": false}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/colaboraciones/" + id + "/retirar").with(tokenDe("luz.mar@duocuc.cl")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Una solicitud que no existe: 404")
    void inexistente() throws Exception {
        mockMvc.perform(patch("/api/v1/colaboraciones/" + UUID.randomUUID() + "/retirar").with(tokenDe("luz.mar@duocuc.cl")))
                .andExpect(status().isNotFound());
    }

    private String pedir(String correo, String destinatario, String proyecto) throws Exception {
        String cuerpo = "{\"solicitadoId\": \"" + destinatario + "\", \"proyectoId\": \"" + proyecto + "\"}";
        String respuesta = mockMvc.perform(post("/api/v1/colaboraciones").with(tokenDe(correo))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private static RequestPostProcessor tokenDe(String correo) {
        return jwt().jwt(token -> token.claim("oid", "oid-" + correo).claim("email", correo).claim("name", correo))
                .authorities(new SimpleGrantedAuthority("ROLE_ESTUDIANTE"));
    }
}
