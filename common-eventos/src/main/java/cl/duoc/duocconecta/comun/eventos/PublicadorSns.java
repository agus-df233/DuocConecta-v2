package cl.duoc.duocconecta.comun.eventos;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica en el tópico SNS. El tipo va como atributo: es lo que miran los filtros de cada cola.
 *
 * <p>Con la mensajería deshabilitada o sin ARN solo registra el evento, así los tests y el
 * entorno local funcionan sin AWS.</p>
 */
public class PublicadorSns implements PublicadorEventos {

    private static final Logger log = LoggerFactory.getLogger(PublicadorSns.class);

    private final SnsClient clienteSns;
    private final JsonMapper jsonMapper;
    private final PropiedadesMensajeria propiedades;

    public PublicadorSns(SnsClient clienteSns, JsonMapper jsonMapper, PropiedadesMensajeria propiedades) {
        this.clienteSns = clienteSns;
        this.jsonMapper = jsonMapper;
        this.propiedades = propiedades;
    }

    @Override
    public void publicar(EventoDuocConecta evento) {
        String arn = propiedades.getTopico().getArn();
        if (!propiedades.isHabilitada() || arn == null || arn.isBlank()) {
            log.info("Mensajería deshabilitada: evento {} '{}' solo se registra", evento.idEvento(), evento.tipo());
            return;
        }

        String idMensaje = clienteSns.publish(PublishRequest.builder()
                .topicArn(arn)
                .message(jsonMapper.writeValueAsString(evento))
                .messageAttributes(atributos(evento))
                .build()).messageId();

        log.info("Evento {} '{}' publicado en SNS (mensaje {})", evento.idEvento(), evento.tipo(), idMensaje);
    }

    /** SNS rechaza atributos vacíos, así que el identificador de petición solo va si existe. */
    private static Map<String, MessageAttributeValue> atributos(EventoDuocConecta evento) {
        Map<String, MessageAttributeValue> atributos = new LinkedHashMap<>();
        atributos.put(AtributosMensaje.TIPO, texto(evento.tipo()));
        atributos.put(AtributosMensaje.ORIGEN, texto(evento.origen()));
        String idPeticion = MDC.get(AtributosMensaje.CLAVE_MDC);
        if (idPeticion != null && !idPeticion.isBlank()) {
            atributos.put(AtributosMensaje.ID_PETICION, texto(idPeticion));
        }
        return atributos;
    }

    private static MessageAttributeValue texto(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }
}
