package cl.duoc.duocconecta.notificaciones.consumidor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

/**
 * El "NACK sin reencolar": un mensaje sin arreglo se copia a la DLQ de su cola, con el motivo
 * como atributo. Se prueba contra un cliente de SQS simulado.
 */
class EnvioADescarteTest {

    private static final String DLQ = "duocconecta-notificaciones-proyectos-descartes";
    private static final String URL_DLQ = "https://sqs.us-east-1.amazonaws.com/000000000000/" + DLQ;

    private SqsAsyncClient sqs;
    private EnvioADescarte envio;

    @BeforeEach
    void preparar() {
        // Los métodos con Consumer son default y delegan en los que reciben el request: esos se simulan.
        sqs = mock(SqsAsyncClient.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(CompletableFuture.completedFuture(GetQueueUrlResponse.builder().queueUrl(URL_DLQ).build()))
                .when(sqs).getQueueUrl(any(GetQueueUrlRequest.class));
        doReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("m-1").build()))
                .when(sqs).sendMessage(any(SendMessageRequest.class));

        PropiedadesMensajeria propiedades = new PropiedadesMensajeria();
        propiedades.setDescartes(Map.of("proyectos", DLQ));
        envio = new EnvioADescarte(sqs, propiedades);
    }

    private List<SendMessageRequest> enviados(int veces) {
        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqs, times(veces)).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("Copia el cuerpo intacto a la DLQ, con el motivo y el X-Request-Id como atributos")
    void copiaALaDlqConMotivo() throws Exception {
        envio.enviar("proyectos", "{roto", "JSON inválido", "req-123");

        SendMessageRequest enviado = enviados(1).getFirst();
        assertThat(enviado.queueUrl()).isEqualTo(URL_DLQ);
        assertThat(enviado.messageBody()).isEqualTo("{roto");
        assertThat(enviado.messageAttributes().get(AtributosMensaje.MOTIVO_DESCARTE).stringValue()).isEqualTo("JSON inválido");
        assertThat(enviado.messageAttributes().get(AtributosMensaje.ID_PETICION).stringValue()).isEqualTo("req-123");
    }

    @Test
    @DisplayName("La URL de la DLQ se pide una sola vez y se reutiliza")
    void urlEnCache() throws Exception {
        envio.enviar("proyectos", "a", "motivo", null);
        envio.enviar("proyectos", "b", "motivo", null);

        verify(sqs, times(1)).getQueueUrl(any(GetQueueUrlRequest.class));
        enviados(2);
    }

    @Test
    @DisplayName("Cuerpo vacío, motivo largo y sin X-Request-Id: SQS igual acepta el mensaje")
    void casosBorde() throws Exception {
        envio.enviar("proyectos", "  ", "x".repeat(400), "");

        SendMessageRequest enviado = enviados(1).getFirst();
        // SQS rechaza un cuerpo vacío: se reemplaza por un texto visible.
        assertThat(enviado.messageBody()).isEqualTo("(vacío)");
        assertThat(enviado.messageAttributes().get(AtributosMensaje.MOTIVO_DESCARTE).stringValue()).hasSize(250);
        assertThat(enviado.messageAttributes()).doesNotContainKey(AtributosMensaje.ID_PETICION);
    }

    @Test
    @DisplayName("Una cola sin DLQ declarada falla con un mensaje claro, sin enviar nada")
    void colaSinDlq() {
        assertThatThrownBy(() -> envio.enviar("desconocida", "x", "motivo", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("descartes.desconocida");
        verify(sqs, times(0)).sendMessage(any(SendMessageRequest.class));
    }
}
