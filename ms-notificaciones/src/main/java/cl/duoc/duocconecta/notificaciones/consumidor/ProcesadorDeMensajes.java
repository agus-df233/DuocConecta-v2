package cl.duoc.duocconecta.notificaciones.consumidor;

import cl.duoc.duocconecta.comun.eventos.AtributosMensaje;
import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.PropiedadesMensajeria;
import cl.duoc.duocconecta.notificaciones.domain.EventoProcesado;
import cl.duoc.duocconecta.notificaciones.exception.EventoNoProcesableException;
import cl.duoc.duocconecta.notificaciones.repository.RepositorioEventosProcesados;
import io.awspring.cloud.sqs.listener.Visibility;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.time.Instant;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * La tabla de decisiones de confirmación, en un solo lugar y para los dos brokers. Todos los
 * consumidores confirman a mano (SQS: {@code acknowledgementMode = "MANUAL"}; RabbitMQ:
 * {@code ackMode = "MANUAL"}) y delegan aquí. Cada broker cumple la decisión con su
 * {@link Confirmacion}:
 *
 * <table>
 *   <tr><th>Caso</th><th>Decisión</th><th>SQS</th><th>RabbitMQ</th><th>Log</th></tr>
 *   <tr><td>Procesado</td><td>confirmar</td><td>{@code acknowledge()}</td><td>{@code basicAck}</td><td>INFO</td></tr>
 *   <tr><td>Duplicado (ya en {@code evento_procesado})</td><td>confirmar sin volver a actuar</td>
 *       <td>{@code acknowledge()}</td><td>{@code basicAck}</td><td>INFO</td></tr>
 *   <tr><td>Error recuperable (base caída, timeout)</td><td>reintentar</td>
 *       <td>sin ack, más visibilidad; a la 3.ª recepción la redrive lo lleva a la DLQ</td>
 *       <td>{@code basicNack(requeue)}; al pasar {@code x-delivery-limit} va al DLX</td><td>WARN</td></tr>
 *   <tr><td>No recuperable (JSON inválido, sin {@code idEvento} o {@code tipo}, versión no soportada)</td>
 *       <td>descartar: el "NACK sin reencolar"</td><td>copia a la DLQ + {@code acknowledge()}</td>
 *       <td>{@code basicNack} sin reencolar → DLX</td><td>ERROR</td></tr>
 * </table>
 *
 * <p>El registro de procesados y la acción van en la misma transacción: si la acción falla, el
 * registro se revierte y el reintento vuelve a procesar. Como SQS y RabbitMQ entregan el mismo
 * evento, el registro también evita que se procese dos veces: el segundo broker en llegar
 * encuentra el evento ya procesado y solo confirma.</p>
 */
@Component
public class ProcesadorDeMensajes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorDeMensajes.class);

    /** Detalle que provoca un fallo recuperable, solo si está permitido. Sirve para la demo. */
    static final String DETALLE_FALLO_SIMULADO = "simular-fallo-recuperable";

    private final JsonMapper jsonMapper;
    private final TransactionTemplate transaccion;
    private final RepositorioEventosProcesados procesados;
    private final EnvioADescarte envioADescarte;
    private final PropiedadesMensajeria propiedades;
    private final boolean permitirFallosSimulados;

    public ProcesadorDeMensajes(JsonMapper jsonMapper, TransactionTemplate transaccion,
                                RepositorioEventosProcesados procesados, EnvioADescarte envioADescarte,
                                PropiedadesMensajeria propiedades,
                                @Value("${duocconecta.mensajeria.permitir-fallos-simulados:false}")
                                boolean permitirFallosSimulados) {
        this.jsonMapper = jsonMapper;
        this.transaccion = transaccion;
        this.procesados = procesados;
        this.envioADescarte = envioADescarte;
        this.propiedades = propiedades;
        this.permitirFallosSimulados = permitirFallosSimulados;
    }

    /**
     * Procesa un mensaje de SQS y decide su confirmación.
     *
     * @param consumidor nombre del consumidor, parte de la clave de idempotencia
     * @param claveCola  clave lógica de la cola en {@code duocconecta.mensajeria.colas}
     * @param intento    cabecera {@code ApproximateReceiveCount}: cuántas veces lo entregó SQS
     * @param accion     lo que hace el consumidor con el evento, dentro de la transacción
     */
    public void procesar(String consumidor, String claveCola, String cuerpo, String idPeticion,
                         String intento, Acknowledgement ack, Visibility visibilidad,
                         Consumer<EventoDuocConecta> accion) {
        Confirmacion sqs = new ConfirmacionSqs(ack, visibilidad, envioADescarte, claveCola, cuerpo, idPeticion,
                propiedades.getParametros().getMaxRecepciones(), propiedades.descarte(claveCola));
        procesar(consumidor, cuerpo, idPeticion, intento, sqs, accion);
    }

    /**
     * Procesa un mensaje de cualquier broker y le pide a su {@link Confirmacion} lo que corresponde.
     *
     * @param intento número de entrega (1 = la primera)
     */
    public void procesar(String consumidor, String cuerpo, String idPeticion, String intento,
                         Confirmacion confirmacion, Consumer<EventoDuocConecta> accion) {
        MDC.put(AtributosMensaje.CLAVE_MDC, idPeticion == null || idPeticion.isBlank() ? "sin-id" : idPeticion);
        try {
            EventoDuocConecta evento = leer(cuerpo);
            boolean procesado = Boolean.TRUE.equals(transaccion.execute(estado -> {
                EventoProcesado.Clave clave = new EventoProcesado.Clave(evento.idEvento(), consumidor);
                if (procesados.existsById(clave)) {
                    return false;
                }
                // Flush antes de actuar: si el otro broker entrega el mismo evento a la vez, su
                // INSERT espera al nuestro y choca con la PK ANTES de ejecutar la acción. Con un
                // save sin flush, el choque llegaba recién al commit y la acción corría dos veces.
                procesados.saveAndFlush(new EventoProcesado(clave, Instant.now()));
                if (permitirFallosSimulados && DETALLE_FALLO_SIMULADO.equals(evento.detalle())) {
                    throw new IllegalStateException("Fallo recuperable simulado para la demo");
                }
                accion.accept(evento);
                return true;
            }));
            confirmacion.confirmar();
            if (procesado) {
                log.info("{}: evento {} '{}' procesado", consumidor, evento.idEvento(), evento.tipo());
            } else {
                log.info("{}: evento {} repetido, se confirma sin volver a procesarlo", consumidor, evento.idEvento());
            }
        } catch (DataIntegrityViolationException carrera) {
            // Dos entregas del mismo evento al mismo tiempo (por ejemplo, una de cada broker):
            // la PK rechazó la segunda.
            confirmacion.confirmar();
            log.info("{}: entrega duplicada concurrente, se confirma", consumidor);
        } catch (EventoNoProcesableException noProcesable) {
            descartar(consumidor, noProcesable.getMessage(), confirmacion);
        } catch (RuntimeException recuperable) {
            reintentar(consumidor, recuperable, intento, confirmacion);
        } finally {
            MDC.remove(AtributosMensaje.CLAVE_MDC);
        }
    }

    /** Valida la forma del mensaje. Un tipo desconocido pero bien formado sí se acepta. */
    private EventoDuocConecta leer(String cuerpo) {
        EventoDuocConecta evento;
        try {
            evento = jsonMapper.readValue(cuerpo, EventoDuocConecta.class);
        } catch (JacksonException | IllegalArgumentException invalido) {
            throw new EventoNoProcesableException("JSON inválido: " + invalido.getMessage(), invalido);
        }
        if (evento == null || vacio(evento.idEvento()) || vacio(evento.tipo())) {
            throw new EventoNoProcesableException("Falta idEvento o tipo");
        }
        if (evento.version() != EventoDuocConecta.VERSION_ACTUAL) {
            throw new EventoNoProcesableException("Versión de contrato no soportada: " + evento.version());
        }
        return evento;
    }

    /** No recuperable: a la DLQ de inmediato. Si ni eso funciona, se deja para que el broker lo reintente. */
    private void descartar(String consumidor, String motivo, Confirmacion confirmacion) {
        try {
            confirmacion.descartar(motivo);
            log.error("{}: mensaje no procesable enviado a {}. Motivo: {}", consumidor, confirmacion.dlq(), motivo);
        } catch (Exception envioFallido) {
            log.error("{}: no se pudo mandar a la DLQ ({}); queda para la redrive. Motivo original: {}",
                    consumidor, envioFallido.getMessage(), motivo);
        }
    }

    /** Recuperable: sin ack. El broker lo vuelve a entregar y, al agotar las entregas, lo manda a la DLQ. */
    private void reintentar(String consumidor, RuntimeException error, String intento, Confirmacion confirmacion) {
        int numero = intento == null || intento.isBlank() ? 1 : Integer.parseInt(intento);
        int maximo = confirmacion.maxEntregas();
        int espera = numero >= maximo ? 0 : Math.min(10 * numero, 60);
        try {
            confirmacion.reintentar(espera);
        } catch (RuntimeException sinCambio) {
            log.warn("{}: no se pudo pedir el reintento: {}", consumidor, sinCambio.getMessage());
        }
        if (numero >= maximo) {
            log.warn("{}: intento {} de {} fallido ({}). El broker lo moverá a {}",
                    consumidor, numero, maximo, error.getMessage(), confirmacion.dlq());
        } else {
            log.warn("{}: intento {} de {} fallido ({}). Se reintenta", consumidor, numero, maximo, error.getMessage());
        }
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
