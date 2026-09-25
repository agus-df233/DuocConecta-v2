package cl.duoc.duocconecta.comun.eventos.topologia;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Crea en AWS lo que falte de la topología. Es idempotente y nunca borra.
 *
 * <p>Lo que ya existe no se recrea ni se reconfigura: si difiere de lo declarado, se avisa en el
 * log y se deja como está. Cambiarlo es una decisión del administrador, no del arranque.</p>
 */
public class ProvisionadorAws {

    private static final Logger log = LoggerFactory.getLogger(ProvisionadorAws.class);

    private final SqsClient clienteSqs;
    private final SnsClient clienteSns;
    private final JsonMapper jsonMapper;

    public ProvisionadorAws(SqsClient clienteSqs, SnsClient clienteSns, JsonMapper jsonMapper) {
        this.clienteSqs = clienteSqs;
        this.clienteSns = clienteSns;
        this.jsonMapper = jsonMapper;
    }

    /** {@code createTopic} ya es idempotente: si existe, devuelve su ARN. */
    public String asegurarTopico(Topico topico) {
        return clienteSns.createTopic(r -> r.name(topico.nombre())).topicArn();
    }

    /** Crea la cola si no existe. Una cola de trabajo exige que su DLQ ya exista. */
    public String asegurarCola(Cola cola) {
        if (urlSiExiste(cola.nombre()).isPresent()) {
            log.info("Cola {} ya existe: no se modifica", cola.nombre());
            return arn(cola.nombre());
        }

        Map<QueueAttributeName, String> atributos = new EnumMap<>(QueueAttributeName.class);
        atributos.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(cola.retencionSegundos()));
        atributos.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(cola.visibilidadSegundos()));
        atributos.put(QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, String.valueOf(cola.esperaSegundos()));
        // SSE con la clave de SQS: con una clave KMS administrada por AWS, SNS no podría escribir.
        atributos.put(QueueAttributeName.SQS_MANAGED_SSE_ENABLED, "true");
        if (!cola.esDescarte()) {
            atributos.put(QueueAttributeName.REDRIVE_POLICY, politicaRedrive(arn(cola.colaDescarte()), cola.maxRecepciones()));
        }

        clienteSqs.createQueue(r -> r.queueName(cola.nombre()).attributes(atributos));
        log.info("Cola {} creada", cola.nombre());
        return arn(cola.nombre());
    }

    /**
     * Deja que SOLO este tópico escriba en la cola, conservando lo que la política ya tenga.
     *
     * <p>Sin esto SNS descarta los mensajes y ningún log lo dice.</p>
     */
    public void asegurarPermisoTopico(String nombreCola, String topicoArn) {
        String url = url(nombreCola);
        String colaArn = arn(nombreCola);
        String actual = clienteSqs.getQueueAttributes(r -> r.queueUrl(url).attributeNames(QueueAttributeName.POLICY))
                .attributes().get(QueueAttributeName.POLICY);

        ObjectNode politica = actual == null || actual.isBlank()
                ? jsonMapper.createObjectNode().put("Version", "2012-10-17")
                : (ObjectNode) jsonMapper.readTree(actual);
        ArrayNode sentencias = politica.has("Statement") && politica.get("Statement").isArray()
                ? (ArrayNode) politica.get("Statement")
                : politica.putArray("Statement");

        for (JsonNode sentencia : sentencias) {
            if (sentencia.toString().contains(topicoArn)) {
                log.info("La cola {} ya permite al tópico escribir", nombreCola);
                return;
            }
        }

        ObjectNode nueva = sentencias.addObject();
        nueva.put("Sid", "SnsEscribe-" + topicoArn.substring(topicoArn.lastIndexOf(':') + 1));
        nueva.put("Effect", "Allow");
        nueva.putObject("Principal").put("Service", "sns.amazonaws.com");
        nueva.put("Action", "sqs:SendMessage");
        nueva.put("Resource", colaArn);
        nueva.putObject("Condition").putObject("ArnEquals").put("aws:SourceArn", topicoArn);

        clienteSqs.setQueueAttributes(r -> r.queueUrl(url)
                .attributes(Map.of(QueueAttributeName.POLICY, jsonMapper.writeValueAsString(politica))));
        log.info("Permiso para el tópico agregado a la cola {}", nombreCola);
    }

    /**
     * Suscribe la cola al tópico con entrega cruda, filtro y DLQ.
     *
     * <p>Si la suscripción existe, solo completa lo que falta. Un filtro distinto al declarado
     * no se pisa: se avisa.</p>
     */
    public String asegurarSuscripcion(Suscripcion suscripcion, String topicoArn) {
        String colaArn = arn(suscripcion.cola());
        String redriveSns = "{\"deadLetterTargetArn\":\"" + arn(suscripcion.colaNoEntregados()) + "\"}";

        Optional<String> existente = suscripcionDe(topicoArn, colaArn);
        if (existente.isEmpty()) {
            Map<String, String> atributos = new LinkedHashMap<>();
            atributos.put("RawMessageDelivery", "true");
            atributos.put("RedrivePolicy", redriveSns);
            if (suscripcion.politicaDeFiltro() != null) {
                atributos.put("FilterPolicyScope", "MessageAttributes");
                atributos.put("FilterPolicy", suscripcion.politicaDeFiltro());
            }
            String arnSuscripcion = clienteSns.subscribe(r -> r.topicArn(topicoArn).protocol("sqs")
                    .endpoint(colaArn).attributes(atributos).returnSubscriptionArn(true)).subscriptionArn();
            log.info("Cola {} suscrita al tópico", suscripcion.cola());
            return arnSuscripcion;
        }

        String arnSuscripcion = existente.get();
        Map<String, String> actuales = clienteSns.getSubscriptionAttributes(r -> r.subscriptionArn(arnSuscripcion))
                .attributes();
        if (!"true".equals(actuales.get("RawMessageDelivery"))) {
            fijar(arnSuscripcion, "RawMessageDelivery", "true");
        }
        if (actuales.get("RedrivePolicy") == null) {
            fijar(arnSuscripcion, "RedrivePolicy", redriveSns);
        }
        String filtroActual = actuales.get("FilterPolicy");
        if (suscripcion.politicaDeFiltro() != null && filtroActual == null) {
            fijar(arnSuscripcion, "FilterPolicyScope", "MessageAttributes");
            fijar(arnSuscripcion, "FilterPolicy", suscripcion.politicaDeFiltro());
        } else if (!mismoFiltro(filtroActual, suscripcion.politicaDeFiltro())) {
            log.warn("La suscripción de {} tiene el filtro {} y se declaró {}: no se modifica",
                    suscripcion.cola(), filtroActual, suscripcion.politicaDeFiltro());
        }
        return arnSuscripcion;
    }

    /** La URL de la cola, que es como SQS la identifica en cada llamada. */
    public String url(String nombreCola) {
        return clienteSqs.getQueueUrl(r -> r.queueName(nombreCola)).queueUrl();
    }

    public String arn(String nombreCola) {
        return clienteSqs.getQueueAttributes(r -> r.queueUrl(url(nombreCola))
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    public Optional<String> urlSiExiste(String nombreCola) {
        try {
            return Optional.of(url(nombreCola));
        } catch (QueueDoesNotExistException noExiste) {
            return Optional.empty();
        }
    }

    /** Redrive de una cola de trabajo hacia su DLQ. SQS espera el JSON como texto. */
    public static String politicaRedrive(String dlqArn, int maxRecepciones) {
        return "{\"deadLetterTargetArn\":\"" + dlqArn + "\",\"maxReceiveCount\":\"" + maxRecepciones + "\"}";
    }

    private Optional<String> suscripcionDe(String topicoArn, String colaArn) {
        return clienteSns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicoArn)).subscriptions().stream()
                .filter(s -> "sqs".equals(s.protocol()) && colaArn.equals(s.endpoint()))
                .map(Subscription::subscriptionArn)
                .findFirst();
    }

    private void fijar(String arnSuscripcion, String atributo, String valor) {
        clienteSns.setSubscriptionAttributes(r -> r.subscriptionArn(arnSuscripcion)
                .attributeName(atributo).attributeValue(valor));
        log.info("Suscripción {}: {} completado", arnSuscripcion, atributo);
    }

    /** Compara filtros como JSON, así los espacios o el orden de las claves no cuentan. */
    private boolean mismoFiltro(String actual, String declarado) {
        if (actual == null || declarado == null) {
            return actual == null && declarado == null;
        }
        return jsonMapper.readTree(actual).equals(jsonMapper.readTree(declarado));
    }
}
