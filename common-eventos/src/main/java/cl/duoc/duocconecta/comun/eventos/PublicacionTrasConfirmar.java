package cl.duoc.duocconecta.comun.eventos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica en SNS solo después de que la transacción se confirma.
 *
 * <p>Los servicios de negocio hacen {@code publishEvent(evento)} y no saben de SNS. Si se
 * publicara dentro de la transacción y esta fallara después, saldría el aviso de algo que no
 * pasó. Si SNS falla aquí, el cambio ya quedó guardado: se registra y el negocio sigue.</p>
 */
public class PublicacionTrasConfirmar {

    private static final Logger log = LoggerFactory.getLogger(PublicacionTrasConfirmar.class);

    private final PublicadorEventos publicador;

    public PublicacionTrasConfirmar(PublicadorEventos publicador) {
        this.publicador = publicador;
    }

    /** {@code fallbackExecution}: también publica si el evento se emite fuera de una transacción. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void alConfirmar(EventoDuocConecta evento) {
        try {
            publicador.publicar(evento);
        } catch (RuntimeException error) {
            // El evento se pierde (el patrón outbox que lo evita queda para la EP3): que quede rastro.
            log.error("No se pudo publicar el evento {} '{}': {}", evento.idEvento(), evento.tipo(),
                    error.getMessage(), error);
        }
    }
}
