package cl.duoc.duocconecta.notificaciones.service;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.notificaciones.domain.EventoAuditado;
import cl.duoc.duocconecta.notificaciones.repository.RepositorioEventosAuditados;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deja constancia de todo evento que pasó por la plataforma. */
@Service
@RequiredArgsConstructor
public class ServicioAuditoria {

    private final RepositorioEventosAuditados repositorio;

    @Transactional
    public void registrar(EventoDuocConecta evento, String idPeticion) {
        repositorio.save(EventoAuditado.builder()
                .idEvento(evento.idEvento())
                .tipo(evento.tipo())
                .origen(evento.origen())
                .actorId(evento.actorId())
                .destinatarioId(evento.destinatarioId())
                .recursoId(evento.recursoId())
                .proyectoId(evento.proyectoId())
                .detalle(evento.detalle())
                .ocurridoEn(evento.ocurridoEn())
                .recibidoEn(Instant.now())
                .idPeticion(idPeticion)
                .build());
    }
}
