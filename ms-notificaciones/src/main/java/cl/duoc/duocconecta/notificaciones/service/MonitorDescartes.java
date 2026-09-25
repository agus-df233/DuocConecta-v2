package cl.duoc.duocconecta.notificaciones.service;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.notificaciones.dto.EstadoCola;
import cl.duoc.duocconecta.notificaciones.dto.MensajeDescartado;
import cl.duoc.duocconecta.notificaciones.exception.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/**
 * Mira las DLQ sin vaciarlas.
 *
 * <p>No hay un consumidor que las vacíe a propósito: lo descartado se queda para revisarlo y, una
 * vez corregida la causa, devolverlo a su cola con el redrive de ms-mensajeria.</p>
 */
@Service
public class MonitorDescartes {

    private final SqsClient clienteSqs;
    private final PropiedadesMensajeria propiedades;

    public MonitorDescartes(SqsClient clienteSqs, PropiedadesMensajeria propiedades) {
        this.clienteSqs = clienteSqs;
        this.propiedades = propiedades;
    }

    /** Las DLQ que vigila este servicio: una por cola de trabajo, más la de SNS. */
    public List<String> colasDeDescarte() {
        List<String> nombres = new ArrayList<>(propiedades.getDescartes().values());
        if (propiedades.getNoEntregados() != null) {
            nombres.add(propiedades.getNoEntregados());
        }
        return nombres;
    }

    public List<EstadoCola> estado() {
        return colasDeDescarte().stream().map(nombre -> new EstadoCola(nombre, mensajes(nombre))).toList();
    }

    /**
     * Hasta 10 mensajes de una DLQ. Con visibilidad 0 vuelven a estar disponibles al tiro: es
     * mirar, no consumir.
     */
    public List<MensajeDescartado> muestra(String nombre) {
        if (!colasDeDescarte().contains(nombre)) {
            throw new RecursoNoEncontradoException("No es una cola de descarte de este servicio: " + nombre);
        }
        String url = clienteSqs.getQueueUrl(r -> r.queueName(nombre)).queueUrl();
        return clienteSqs.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(10).visibilityTimeout(0)
                        .waitTimeSeconds(1).messageAttributeNames("All")
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                .messages().stream().map(MonitorDescartes::aDescartado).toList();
    }

    public long mensajes(String nombre) {
        String url = clienteSqs.getQueueUrl(r -> r.queueName(nombre)).queueUrl();
        String valor = clienteSqs.getQueueAttributes(r -> r.queueUrl(url)
                        .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
        return valor == null ? 0 : Long.parseLong(valor);
    }

    private static MensajeDescartado aDescartado(Message mensaje) {
        Map<String, MessageAttributeValue> atributos = mensaje.messageAttributes();
        String recepciones = mensaje.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        return new MensajeDescartado(mensaje.messageId(), mensaje.body(),
                texto(atributos, AtributosMensaje.MOTIVO_DESCARTE), texto(atributos, AtributosMensaje.ID_PETICION),
                recepciones == null ? 0 : Integer.parseInt(recepciones));
    }

    private static String texto(Map<String, MessageAttributeValue> atributos, String nombre) {
        MessageAttributeValue valor = atributos.get(nombre);
        return valor == null ? null : valor.stringValue();
    }
}
