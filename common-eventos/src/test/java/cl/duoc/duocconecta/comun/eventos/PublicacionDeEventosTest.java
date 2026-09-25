package cl.duoc.duocconecta.comun.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.duocconecta.comun.eventos.topologia.Suscripcion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import tools.jackson.databind.json.JsonMapper;

/** Publicación en SNS sin AWS: el cliente es un doble. */
class PublicacionDeEventosTest {

    private final SnsClient clienteSns = mock(SnsClient.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private static EventoDuocConecta evento() {
        return EventoDuocConecta.nuevo(TiposDeEvento.COLABORACION_ACEPTADA, "ms-contacto",
                "oid-actor", "oid-destino", "solicitud-1", "proyecto-1", null);
    }

    private static PropiedadesMensajeria propiedades(boolean habilitada, String arn) {
        PropiedadesMensajeria propiedades = new PropiedadesMensajeria();
        propiedades.setHabilitada(habilitada);
        propiedades.getTopico().setArn(arn);
        return propiedades;
    }

    @AfterEach
    void limpiarMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Con la mensajería deshabilitada no se llama a SNS")
    void deshabilitadaNoPublica() {
        new PublicadorSns(clienteSns, jsonMapper, propiedades(false, "arn:aws:sns:x:1:t")).publicar(evento());
        verify(clienteSns, never()).publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("Sin ARN no se llama a SNS (entorno local)")
    void sinArnNoPublica() {
        new PublicadorSns(clienteSns, jsonMapper, propiedades(true, "")).publicar(evento());
        verify(clienteSns, never()).publish(any(PublishRequest.class));
    }

    @Test
    @DisplayName("Publica con tipo, origen y X-Request-Id como atributos")
    void publicaConAtributos() {
        when(clienteSns.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("m-1").build());
        MDC.put(AtributosMensaje.CLAVE_MDC, "demo-ep2");
        EventoDuocConecta evento = evento();

        new PublicadorSns(clienteSns, jsonMapper, propiedades(true, "arn:aws:sns:x:1:t")).publicar(evento);

        ArgumentCaptor<PublishRequest> captura = ArgumentCaptor.forClass(PublishRequest.class);
        verify(clienteSns).publish(captura.capture());
        PublishRequest pedido = captura.getValue();
        assertThat(pedido.topicArn()).isEqualTo("arn:aws:sns:x:1:t");
        assertThat(pedido.messageAttributes().get("tipo").stringValue()).isEqualTo("colaboracion.aceptada");
        assertThat(pedido.messageAttributes().get("origen").stringValue()).isEqualTo("ms-contacto");
        assertThat(pedido.messageAttributes().get("X-Request-Id").stringValue()).isEqualTo("demo-ep2");
        assertThat(pedido.message()).contains(evento.idEvento()).doesNotContain("@");
    }

    @Test
    @DisplayName("Sin identificador de petición no se manda un atributo vacío")
    void sinIdPeticionNoVaElAtributo() {
        when(clienteSns.publish(any(PublishRequest.class)))
                .thenReturn(PublishResponse.builder().messageId("m-1").build());
        new PublicadorSns(clienteSns, jsonMapper, propiedades(true, "arn:aws:sns:x:1:t")).publicar(evento());
        ArgumentCaptor<PublishRequest> captura = ArgumentCaptor.forClass(PublishRequest.class);
        verify(clienteSns).publish(captura.capture());
        assertThat(captura.getValue().messageAttributes()).doesNotContainKey("X-Request-Id");
    }

    @Test
    @DisplayName("Si SNS falla después del commit, el error se registra y no sube al negocio")
    void falloDeSnsNoRompeElNegocio() {
        PublicadorEventos publicador = mock(PublicadorEventos.class);
        doThrow(new IllegalStateException("SNS caído")).when(publicador).publicar(any());
        assertThatCode(() -> new PublicacionTrasConfirmar(publicador).alConfirmar(evento()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("El filtro por prefijo se arma sobre el atributo tipo; sin prefijo no hay filtro")
    void politicaDeFiltro() {
        assertThat(new Suscripcion("t", "c", "proyecto.", "n").politicaDeFiltro())
                .isEqualTo("{\"tipo\":[{\"prefix\":\"proyecto.\"}]}");
        assertThat(new Suscripcion("t", "c", null, "n").politicaDeFiltro()).isNull();
    }
}
