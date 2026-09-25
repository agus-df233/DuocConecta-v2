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
 * La tabla de decisiones de confirmación, en un solo lugar. Todos los consumidores confirman a
 * mano ({@code acknowledgementMode = "MANUAL"}) y delegan aquí:
 *
 * <table>
 *   <tr><th>Caso</th><th>Acción</th><th>Log</th></tr>
 *   <tr><td>Procesado</td><td>{@code acknowledge()}</td><td>INFO</td></tr>
 *   <tr><td>Duplicado (ya en {@code evento_procesado})</td><td>{@code acknowledge()} sin volver a actuar</td><td>INFO</td></tr>
 *   <tr><td>Error recuperable (base caída, timeout)</td><td>Sin ack y más visibilidad (backoff).
 *       SQS lo reentrega; a la recepción {@code maxRecepciones} la redrive lo lleva a la DLQ</td><td>WARN</td></tr>
 *   <tr><td>No recuperable (JSON inválido, sin {@code idEvento} o {@code tipo}, versión no soportada)</td>
 *       <td>Copia a la DLQ con el motivo y {@code acknowledge()}: el "NACK sin reencolar"</td><td>ERROR</td></tr>
 * </table>
 *
 * <p>El registro de procesados y la acción van en la misma transacción: si la acción falla, el
 * registro se revierte y el reintento vuelve a procesar.</p>
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
     * Procesa un mensaje y decide su confirmación.
     *
     * @param consumidor nombre del consumidor, parte de la clave de idempotencia
     * @param claveCola  clave lógica de la cola en {@code duocconecta.mensajeria.colas}
     * @param intento    cabecera {@code ApproximateReceiveCount}: cuántas veces lo entregó SQS
     * @param accion     lo que hace el consumidor con el evento, dentro de la transacción
     */
    public void procesar(String consumidor, String claveCola, String cuerpo, String idPeticion,
                         String intento, Acknowledgement ack, Visibility visibilidad,
                         Consumer<EventoDuocConecta> accion) {
        MDC.put(AtributosMensaje.CLAVE_MDC, idPeticion == null || idPeticion.isBlank() ? "sin-id" : idPeticion);
        try {
            EventoDuocConecta evento = leer(cuerpo);
            boolean procesado = Boolean.TRUE.equals(transaccion.execute(estado -> {
                EventoProcesado.Clave clave = new EventoProcesado.Clave(evento.idEvento(), consumidor);
                if (procesados.existsById(clave)) {
                    return false;
                }
                procesados.save(new EventoProcesado(clave, Instant.now()));
                if (permitirFallosSimulados && DETALLE_FALLO_SIMULADO.equals(evento.detalle())) {
                    throw new IllegalStateException("Fallo recuperable simulado para la demo");
                }
                accion.accept(evento);
                return true;
            }));
            ack.acknowledge();
            if (procesado) {
                log.info("{}: evento {} '{}' procesado", consumidor, evento.idEvento(), evento.tipo());
            } else {
                log.info("{}: evento {} repetido, se confirma sin volver a procesarlo", consumidor, evento.idEvento());
            }
        } catch (DataIntegrityViolationException carrera) {
            // Dos entregas del mismo mensaje al mismo tiempo: la PK rechazó la segunda.
            ack.acknowledge();
            log.info("{}: entrega duplicada concurrente, se confirma", consumidor);
        } catch (EventoNoProcesableException noProcesable) {
            descartar(consumidor, claveCola, cuerpo, noProcesable.getMessage(), idPeticion, ack);
        } catch (RuntimeException recuperable) {
            reintentar(consumidor, recuperable, intento, visibilidad);
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

    /** No recuperable: a la DLQ de inmediato. Si ni eso funciona, se deja para la redrive. */
    private void descartar(String consumidor, String claveCola, String cuerpo, String motivo,
                           String idPeticion, Acknowledgement ack) {
        try {
            envioADescarte.enviar(claveCola, cuerpo, motivo, idPeticion);
            ack.acknowledge();
            log.error("{}: mensaje no procesable enviado a {}. Motivo: {}", consumidor,
                    propiedades.descarte(claveCola), motivo);
        } catch (Exception envioFallido) {
            log.error("{}: no se pudo copiar a la DLQ ({}); queda para la redrive. Motivo original: {}",
                    consumidor, envioFallido.getMessage(), motivo);
        }
    }

    /** Recuperable: sin ack, con más visibilidad en cada intento. SQS lo vuelve a entregar. */
    private void reintentar(String consumidor, RuntimeException error, String intento, Visibility visibilidad) {
        int numero = intento == null ? 1 : Integer.parseInt(intento);
        int maximo = propiedades.getParametros().getMaxRecepciones();
        if (numero >= maximo) {
            log.warn("{}: intento {} de {} fallido ({}). SQS lo moverá a la DLQ al vencer la visibilidad",
                    consumidor, numero, maximo, error.getMessage());
            return;
        }
        int espera = Math.min(10 * numero, 60);
        try {
            visibilidad.changeTo(espera);
        } catch (RuntimeException sinCambio) {
            log.warn("{}: no se pudo ajustar la visibilidad: {}", consumidor, sinCambio.getMessage());
        }
        log.warn("{}: intento {} de {} fallido ({}). Se reintenta en {} s", consumidor, numero, maximo,
                error.getMessage(), espera);
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
