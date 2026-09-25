package cl.duoc.duocconecta.notificaciones.consumidor;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * Manda a la DLQ un mensaje que no tiene arreglo, con el motivo como atributo.
 *
 * <p>En SQS no existe el descarte inmediato: un mensaje rechazado vuelve a la cola hasta agotar
 * sus recepciones. Para no reintentar lo que nunca va a funcionar, se copia a la DLQ a mano.</p>
 */
@Component
public class EnvioADescarte {

    private final SqsAsyncClient clienteSqs;
    private final PropiedadesMensajeria propiedades;
    private final Map<String, String> urls = new ConcurrentHashMap<>();

    public EnvioADescarte(SqsAsyncClient clienteSqs, PropiedadesMensajeria propiedades) {
        this.clienteSqs = clienteSqs;
        this.propiedades = propiedades;
    }

    /** @param claveCola clave lógica de la cola de trabajo (proyectos, colaboraciones, auditoria) */
    public void enviar(String claveCola, String cuerpo, String motivo, String idPeticion) throws Exception {
        String dlq = propiedades.descarte(claveCola);
        String url = urls.computeIfAbsent(dlq, nombre ->
                clienteSqs.getQueueUrl(r -> r.queueName(nombre)).join().queueUrl());

        Map<String, MessageAttributeValue> atributos = new LinkedHashMap<>();
        atributos.put(AtributosMensaje.MOTIVO_DESCARTE, texto(recortar(motivo)));
        if (idPeticion != null && !idPeticion.isBlank()) {
            atributos.put(AtributosMensaje.ID_PETICION, texto(idPeticion));
        }
        clienteSqs.sendMessage(r -> r.queueUrl(url).messageBody(cuerpo == null || cuerpo.isBlank() ? "(vacío)" : cuerpo)
                .messageAttributes(atributos)).get(10, TimeUnit.SECONDS);
    }

    private static MessageAttributeValue texto(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }

    /** SQS acepta atributos largos, pero un motivo de una línea se lee mejor en la consola. */
    private static String recortar(String motivo) {
        return motivo.length() > 250 ? motivo.substring(0, 250) : motivo;
    }
}
