package cl.duoc.duocconecta.notificaciones.service;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.notificaciones.domain.Aviso;
import cl.duoc.duocconecta.notificaciones.dto.AvisoRespuesta;
import cl.duoc.duocconecta.notificaciones.exception.OperacionNoPermitidaException;
import cl.duoc.duocconecta.notificaciones.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.notificaciones.repository.RepositorioAvisos;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La bandeja de avisos: la llenan los consumidores y la lee cada persona. */
@Service
@RequiredArgsConstructor
public class ServicioAvisos {

    private final RepositorioAvisos repositorio;

    /** Deja un aviso para el destinatario del evento. Lo llama un consumidor. */
    @Transactional
    public void crear(EventoDuocConecta evento, String titulo) {
        repositorio.save(Aviso.builder()
                .destinatarioId(evento.destinatarioId())
                .tipo(evento.tipo())
                .titulo(titulo)
                .detalle(evento.detalle())
                .recursoId(evento.recursoId())
                .proyectoId(evento.proyectoId())
                .idEvento(evento.idEvento())
                .leido(false)
                .build());
    }

    @Transactional(readOnly = true)
    public List<AvisoRespuesta> deLaPersona(String oid) {
        return repositorio.findTop100ByDestinatarioIdOrderByCreadoEnDesc(oid).stream()
                .map(AvisoRespuesta::desdeEntidad).toList();
    }

    @Transactional(readOnly = true)
    public long noLeidos(String oid) {
        return repositorio.countByDestinatarioIdAndLeidoFalse(oid);
    }

    /** Solo la dueña del aviso puede marcarlo. */
    @Transactional
    public AvisoRespuesta marcarLeido(UUID id, String oid) {
        Aviso aviso = repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Aviso no encontrado: " + id));
        if (!aviso.getDestinatarioId().equals(oid)) {
            throw new OperacionNoPermitidaException("Este aviso es de otra persona");
        }
        aviso.setLeido(true);
        return AvisoRespuesta.desdeEntidad(repositorio.save(aviso));
    }
}
