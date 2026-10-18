package cl.duoc.duocconecta.mensajeria.service;

import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria.Parametros;
import cl.duoc.duocconecta.comun.eventos.topologia.Cola;
import cl.duoc.duocconecta.comun.eventos.topologia.ProvisionadorAws;
import cl.duoc.duocconecta.comun.eventos.topologia.Suscripcion;
import cl.duoc.duocconecta.comun.eventos.topologia.Topico;
import cl.duoc.duocconecta.mensajeria.dto.ColaRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.CrearColaDatos;
import cl.duoc.duocconecta.mensajeria.dto.CrearSuscripcionDatos;
import cl.duoc.duocconecta.mensajeria.dto.RedriveRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.SuscripcionRespuesta;
import cl.duoc.duocconecta.mensajeria.dto.TopicoRespuesta;
import cl.duoc.duocconecta.mensajeria.exception.ConflictoDeEstadoException;
import cl.duoc.duocconecta.mensajeria.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.mensajeria.exception.SolicitudInvalidaException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.PurgeQueueInProgressException;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Administración de colas, tópicos y suscripciones: el {@code RabbitAdminService} de la pauta.
 *
 * <p>Es el único lugar que llama a SQS y SNS para administrar: los controladores solo usan estos
 * métodos. Los recursos de la topología base están protegidos y no se eliminan desde la API.</p>
 */
@Service
public class ServicioAdministracionColas {

    private static final Logger log = LoggerFactory.getLogger(ServicioAdministracionColas.class);

    /** La API solo ve y toca recursos de la plataforma. */
    private static final String PREFIJO = "duocconecta-";

    private static final String SUFIJO_DESCARTE = "-descartes";

    private final SqsClient clienteSqs;
    private final SnsClient clienteSns;
    private final ProvisionadorAws provisionador;
    private final PropiedadesMensajeria propiedades;
    private final JsonMapper jsonMapper;
    private final Set<String> colasProtegidas = new HashSet<>();

    public ServicioAdministracionColas(SqsClient clienteSqs, SnsClient clienteSns, ProvisionadorAws provisionador,
                                       PropiedadesMensajeria propiedades, JsonMapper jsonMapper) {
        this.clienteSqs = clienteSqs;
        this.clienteSns = clienteSns;
        this.provisionador = provisionador;
        this.propiedades = propiedades;
        this.jsonMapper = jsonMapper;
        colasProtegidas.addAll(propiedades.getColas().values());
        colasProtegidas.addAll(propiedades.getDescartes().values());
        if (propiedades.getNoEntregados() != null) {
            colasProtegidas.add(propiedades.getNoEntregados());
        }
    }

    // ---------------------------------------------------------------- colas

    public List<ColaRespuesta> listarColas() {
        return clienteSqs.listQueuesPaginator(r -> r.queueNamePrefix(PREFIJO)).queueUrls().stream()
                .map(url -> url.substring(url.lastIndexOf('/') + 1))
                .sorted()
                .map(this::detalleCola)
                .toList();
    }

    /** @throws software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException si no existe (404) */
    public ColaRespuesta detalleCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        String url = provisionador.url(nombre);
        Map<QueueAttributeName, String> a = clienteSqs.getQueueAttributes(r -> r.queueUrl(url)
                .attributeNames(QueueAttributeName.ALL)).attributes();

        String colaDescarte = null;
        Integer maxRecepciones = null;
        String redrive = a.get(QueueAttributeName.REDRIVE_POLICY);
        if (redrive != null) {
            JsonNode politica = jsonMapper.readTree(redrive);
            String dlqArn = politica.path("deadLetterTargetArn").asString();
            colaDescarte = dlqArn.substring(dlqArn.lastIndexOf(':') + 1);
            maxRecepciones = Integer.valueOf(politica.path("maxReceiveCount").asString());
        }
        return new ColaRespuesta(nombre, url, a.get(QueueAttributeName.QUEUE_ARN),
                numero(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
                numero(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)),
                entero(a.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)),
                entero(a.get(QueueAttributeName.VISIBILITY_TIMEOUT)),
                colaDescarte, maxRecepciones, colasProtegidas.contains(nombre));
    }

    /** Crea la cola y, si se pide, su DLQ con la redrive. 409 si la cola ya existe. */
    public ColaRespuesta crearCola(CrearColaDatos datos) {
        if (provisionador.urlSiExiste(datos.nombre()).isPresent()) {
            throw new ConflictoDeEstadoException("La cola " + datos.nombre() + " ya existe");
        }
        Parametros base = propiedades.getParametros();
        int retencion = valor(datos.retencionSegundos(), base.getRetencionTrabajoSegundos());
        int visibilidad = valor(datos.visibilidadSegundos(), base.getVisibilidadSegundos());
        int espera = valor(datos.esperaSegundos(), base.getEsperaSegundos());

        if (datos.quiereDescarte()) {
            String dlq = datos.nombre() + SUFIJO_DESCARTE;
            provisionador.asegurarCola(Cola.deDescarte(dlq,
                    valor(datos.retencionDescarteSegundos(), base.getRetencionDescartesSegundos()), visibilidad, false));
            provisionador.asegurarCola(new Cola(datos.nombre(), retencion, visibilidad, espera, dlq,
                    valor(datos.maxRecepciones(), base.getMaxRecepciones()), false));
        } else {
            provisionador.asegurarCola(new Cola(datos.nombre(), retencion, visibilidad, espera, null, 0, false));
        }
        log.info("Administración: cola {} creada{}", datos.nombre(), datos.quiereDescarte() ? " con su DLQ" : "");
        return detalleCola(datos.nombre());
    }

    /** Elimina una cola. SQS tarda hasta 60 s en liberar el nombre. */
    public void eliminarCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        exigirNoProtegida(nombre, colasProtegidas.contains(nombre));
        String url = provisionador.url(nombre);
        clienteSqs.deleteQueue(r -> r.queueUrl(url));
        log.info("Administración: cola {} eliminada", nombre);
    }

    /** Vacía la cola. SQS permite una purga por minuto. */
    public void purgarCola(String nombre) {
        exigirDeLaPlataforma(nombre);
        String url = provisionador.url(nombre);
        try {
            clienteSqs.purgeQueue(r -> r.queueUrl(url));
        } catch (PurgeQueueInProgressException enCurso) {
            throw new ConflictoDeEstadoException("Ya hay una purga en curso: SQS permite una por minuto");
        }
        log.info("Administración: cola {} purgada", nombre);
    }

    /** Devuelve los mensajes de una DLQ a su cola de origen, una vez corregida la causa. */
    public RedriveRespuesta redrive(String nombreDescarte) {
        exigirDeLaPlataforma(nombreDescarte);
        String url = provisionador.url(nombreDescarte);
        boolean esDescarte = !clienteSqs.listDeadLetterSourceQueues(r -> r.queueUrl(url)).queueUrls().isEmpty();
        if (!esDescarte) {
            throw new ConflictoDeEstadoException(nombreDescarte + " no es la DLQ de ninguna cola");
        }
        String arn = provisionador.arn(nombreDescarte);
        String tarea = clienteSqs.startMessageMoveTask(r -> r.sourceArn(arn)).taskHandle();
        log.info("Administración: redrive iniciado desde {}", nombreDescarte);
        return new RedriveRespuesta(nombreDescarte, tarea);
    }

    // ---------------------------------------------------------------- tópicos (exchanges)

    public List<TopicoRespuesta> listarTopicos() {
        return topicos().stream()
                .map(arn -> new TopicoRespuesta(nombreDe(arn), arn, suscripcionesDe(arn).size(), esTopicoProtegido(nombreDe(arn))))
                .toList();
    }

    public TopicoRespuesta crearTopico(String nombre) {
        if (arnDeTopico(nombre).isPresent()) {
            throw new ConflictoDeEstadoException("El tópico " + nombre + " ya existe");
        }
        String arn = provisionador.asegurarTopico(new Topico(nombre));
        log.info("Administración: tópico {} creado", nombre);
        return new TopicoRespuesta(nombre, arn, 0, false);
    }

    public void eliminarTopico(String nombre) {
        exigirNoProtegida(nombre, esTopicoProtegido(nombre));
        String arn = arnDeTopico(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException("El tópico " + nombre + " no existe"));
        clienteSns.deleteTopic(r -> r.topicArn(arn));
        log.info("Administración: tópico {} eliminado", nombre);
    }

    // ---------------------------------------------------------------- suscripciones (bindings)

    public List<SuscripcionRespuesta> listarSuscripciones(String topico) {
        List<String> arns = topico == null ? topicos()
                : List.of(arnDeTopico(topico).orElseThrow(() -> new RecursoNoEncontradoException("El tópico " + topico + " no existe")));
        return arns.stream().flatMap(arn -> suscripcionesDe(arn).stream()).map(this::aRespuesta).toList();
    }

    /** Suscribe la cola con entrega cruda, filtro y DLQ; agrega el permiso de la cola solo. */
    public SuscripcionRespuesta crearSuscripcion(CrearSuscripcionDatos datos) {
        String topicoArn = arnDeTopico(datos.topico())
                .orElseThrow(() -> new RecursoNoEncontradoException("El tópico " + datos.topico() + " no existe"));
        String colaArn = provisionador.arn(datos.cola());
        boolean yaExiste = suscripcionesDe(topicoArn).stream().anyMatch(s -> colaArn.equals(s.endpoint()));
        if (yaExiste) {
            throw new ConflictoDeEstadoException("La cola " + datos.cola() + " ya está suscrita a " + datos.topico());
        }
        String noEntregados = datos.colaNoEntregados() != null ? datos.colaNoEntregados() : propiedades.getNoEntregados();
        provisionador.url(noEntregados);

        provisionador.asegurarPermisoTopico(datos.cola(), topicoArn);
        provisionador.asegurarPermisoTopico(noEntregados, topicoArn);
        String arn = provisionador.asegurarSuscripcion(
                new Suscripcion(datos.topico(), datos.cola(), vacioANulo(datos.filtroPrefijo()), noEntregados), topicoArn);
        log.info("Administración: {} suscrita a {} (filtro {})", datos.cola(), datos.topico(), datos.filtroPrefijo());
        return aRespuesta(clienteSns.getSubscriptionAttributes(r -> r.subscriptionArn(arn)).attributes(), arn);
    }

    /** @param id el final del ARN de la suscripción */
    public void eliminarSuscripcion(String id) {
        Subscription suscripcion = topicos().stream()
                .flatMap(arn -> suscripcionesDe(arn).stream())
                .filter(s -> s.subscriptionArn().endsWith(":" + id))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("La suscripción " + id + " no existe"));
        String cola = nombreDe(suscripcion.endpoint());
        exigirNoProtegida("la suscripción de " + cola,
                esTopicoProtegido(nombreDe(suscripcion.topicArn())) && colasProtegidas.contains(cola));
        clienteSns.unsubscribe(r -> r.subscriptionArn(suscripcion.subscriptionArn()));
        log.info("Administración: suscripción {} eliminada", id);
    }

    // ---------------------------------------------------------------- apoyo

    private List<String> topicos() {
        return clienteSns.listTopicsPaginator().topics().stream()
                .map(Topic::topicArn)
                .filter(arn -> nombreDe(arn).startsWith(PREFIJO))
                .sorted()
                .toList();
    }

    private Optional<String> arnDeTopico(String nombre) {
        return topicos().stream().filter(arn -> nombreDe(arn).equals(nombre)).findFirst();
    }

    private List<Subscription> suscripcionesDe(String topicoArn) {
        return clienteSns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicoArn)).subscriptions().stream()
                .filter(s -> "sqs".equals(s.protocol()))
                .toList();
    }

    private SuscripcionRespuesta aRespuesta(Subscription s) {
        return aRespuesta(clienteSns.getSubscriptionAttributes(r -> r.subscriptionArn(s.subscriptionArn())).attributes(),
                s.subscriptionArn());
    }

    private SuscripcionRespuesta aRespuesta(Map<String, String> a, String arn) {
        String cola = nombreDe(a.getOrDefault("Endpoint", ""));
        String topico = nombreDe(a.getOrDefault("TopicArn", ""));
        String noEntregados = null;
        if (a.get("RedrivePolicy") != null) {
            String dlqArn = jsonMapper.readTree(a.get("RedrivePolicy")).path("deadLetterTargetArn").asString();
            noEntregados = nombreDe(dlqArn);
        }
        return new SuscripcionRespuesta(nombreDe(arn), arn, topico, cola, a.get("FilterPolicy"),
                "true".equals(a.get("RawMessageDelivery")), noEntregados,
                esTopicoProtegido(topico) && colasProtegidas.contains(cola));
    }

    private boolean esTopicoProtegido(String nombre) {
        return nombre.equals(propiedades.getTopico().getNombre());
    }

    /** La API solo toca colas de la plataforma: una cola ajena de la cuenta queda fuera de su alcance. */
    private static void exigirDeLaPlataforma(String nombre) {
        if (nombre == null || !nombre.startsWith(PREFIJO)) {
            throw new SolicitudInvalidaException("Solo se administran colas con el prefijo " + PREFIJO
                    + ": " + nombre + " no es de la plataforma");
        }
    }

    private static void exigirNoProtegida(String recurso, boolean protegida) {
        if (protegida) {
            throw new ConflictoDeEstadoException(recurso + " es parte de la topología base y no se elimina desde la API");
        }
    }

    /** Tanto un ARN de SNS/SQS como un ARN de suscripción terminan en el nombre o en el id. */
    private static String nombreDe(String arn) {
        return arn.substring(arn.lastIndexOf(':') + 1);
    }

    private static int valor(Integer indicado, int porDefecto) {
        return indicado != null ? indicado : porDefecto;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }

    private static long numero(String texto) {
        return texto == null ? 0 : Long.parseLong(texto);
    }

    private static Integer entero(String texto) {
        return texto == null ? null : Integer.valueOf(texto);
    }
}
