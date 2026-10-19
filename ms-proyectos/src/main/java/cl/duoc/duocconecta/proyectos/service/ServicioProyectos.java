package cl.duoc.duocconecta.proyectos.service;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import cl.duoc.duocconecta.proyectos.domain.Adjunto;
import cl.duoc.duocconecta.proyectos.domain.Proyecto;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import cl.duoc.duocconecta.proyectos.dto.AdjuntoDatos;
import cl.duoc.duocconecta.proyectos.dto.ProyectoDatos;
import cl.duoc.duocconecta.proyectos.exception.ConflictoDeEstadoException;
import cl.duoc.duocconecta.proyectos.exception.OperacionNoPermitidaException;
import cl.duoc.duocconecta.proyectos.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.proyectos.exception.SolicitudInvalidaException;
import cl.duoc.duocconecta.proyectos.repository.RepositorioComentarios;
import cl.duoc.duocconecta.proyectos.repository.RepositorioProyectos;
import cl.duoc.duocconecta.proyectos.dto.ProyectoRespuesta;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * La vitrina de proyectos.
 *
 * <p>Devuelve DTOs y no entidades, dentro de la transacción: las listas de colaboradores y
 * adjuntos se cargan de forma perezosa y fuera de ella ya no hay sesión con la base.</p>
 */
@Service
@RequiredArgsConstructor
public class ServicioProyectos {

    private final RepositorioProyectos repositorioProyectos;
    private final RepositorioComentarios repositorioComentarios;
    private final ServicioAdjuntos servicioAdjuntos;
    private final ApplicationEventPublisher eventos;

    /** A DTO, con la cuenta de comentarios. La moderación lo reutiliza. */
    ProyectoRespuesta aRespuesta(Proyecto proyecto) {
        return ProyectoRespuesta.desdeEntidad(
                proyecto, repositorioComentarios.countByProyectoId(proyecto.getId()),
                servicioAdjuntos::urlPublica);
    }

    /**
     * Deja los adjuntos del proyecto igual a lo que llegó, y borra de S3 lo que se quitó.
     *
     * <p>Se reemplaza el contenido y no la lista para que Hibernate siga el rastro de la colección.
     * Los archivos que ya no están en la lista se borran del bucket: si no, cada edición dejaría
     * basura acumulándose.</p>
     */
    void sincronizarAdjuntos(Proyecto proyecto, List<AdjuntoDatos> entrantes) {
        if (entrantes == null) {
            return;
        }
        if (entrantes.size() > ServicioAdjuntos.MAXIMO_POR_PROYECTO) {
            throw new ConflictoDeEstadoException(
                    "Un proyecto admite hasta " + ServicioAdjuntos.MAXIMO_POR_PROYECTO + " adjuntos.");
        }

        Set<String> clavesQueSiguen = entrantes.stream()
                .map(AdjuntoDatos::claveS3).filter(Objects::nonNull).collect(Collectors.toSet());
        proyecto.getAdjuntos().stream()
                .filter(Adjunto::esArchivoSubido)
                .filter(a -> !clavesQueSiguen.contains(a.getClaveS3()))
                .forEach(a -> servicioAdjuntos.borrar(a.getClaveS3()));

        proyecto.getAdjuntos().clear();
        entrantes.forEach(d -> proyecto.agregarAdjunto(aEntidad(d)));
    }

    /** Un adjunto es un archivo subido o un enlace, nunca las dos cosas ni ninguna. */
    private Adjunto aEntidad(AdjuntoDatos d) {
        boolean tieneClave = d.claveS3() != null && !d.claveS3().isBlank();
        boolean tieneUrl = d.urlExterna() != null && !d.urlExterna().isBlank();
        if (tieneClave == tieneUrl) {
            throw new SolicitudInvalidaException(
                    "Cada adjunto tiene que ser un archivo subido o un enlace, no ambos.");
        }
        if (tieneClave) {
            servicioAdjuntos.validarClave(d.claveS3());
        }
        return Adjunto.builder()
                .claveS3(tieneClave ? d.claveS3() : null)
                .urlExterna(tieneUrl ? d.urlExterna() : null)
                .nombre(d.nombre())
                .tipoContenido(d.tipoContenido())
                .tamanoBytes(d.tamanoBytes())
                .build();
    }

    @Transactional
    public ProyectoRespuesta crear(ProyectoDatos dto, String propietarioId) {
        Proyecto repo = Proyecto.builder()
                .nombre(dto.nombre())
                .resumen(dto.resumen())
                .descripcion(dto.descripcion())
                .urlRepositorio(dto.urlRepositorio())
                .propietarioId(propietarioId)
                .sede(dto.sede())
                .estado(dto.estado())
                .visibilidad(dto.visibilidad())
                .colaboradoresIds(dto.visibilidad() == Visibilidad.COMPARTIDO && dto.colaboradoresIds() != null
                        ? dto.colaboradoresIds() : List.of())
                .herramientas(dto.herramientas() != null ? dto.herramientas() : List.of())
                .build();
        sincronizarAdjuntos(repo, dto.adjuntos());
        Proyecto guardado = repositorioProyectos.save(repo);
        publicar(TiposDeEvento.PROYECTO_PUBLICADO, propietarioId, null, guardado);
        return aRespuesta(guardado);
    }

    /**
     * Lista solo lo que el usuario autenticado puede ver:
     * todos los públicos + sus propios privados/compartidos + los compartidos donde es colaborador.
     */
    @Transactional(readOnly = true)
    public List<ProyectoRespuesta> listarVisiblesPara(String usuarioId) {
        List<Proyecto> publicos = repositorioProyectos.findByVisibilidadAndOcultoFalse(Visibilidad.PUBLICO);
        List<Proyecto> propios = repositorioProyectos.findByPropietarioId(usuarioId);
        List<Proyecto> compartidosConmigo = repositorioProyectos
                .findByVisibilidadAndColaboradoresIdsContainingAndOcultoFalse(Visibilidad.COMPARTIDO, usuarioId);

        return Stream.of(publicos, propios, compartidosConmigo)
                .flatMap(List::stream)
                .distinct()
                .map(this::aRespuesta)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProyectoRespuesta obtenerSiVisible(UUID id, String usuarioId) {
        Proyecto repo = repositorioProyectos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));

        if (!repo.esVisiblePara(usuarioId)) {
            throw new OperacionNoPermitidaException("No tienes acceso a este proyecto");
        }
        return aRespuesta(repo);
    }

    /** Permite ocultar sin borrar. Solo el propietario; cualquier otro recibe 403. */
    @Transactional
    public ProyectoRespuesta actualizar(UUID id, ProyectoDatos dto, String usuarioId) {
        Proyecto proyecto = repositorioProyectos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));

        if (!proyecto.getPropietarioId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException("Solo el propietario puede editar este proyecto");
        }
        // Un proyecto abierto lleva carrera y no admite visibilidad: tiene su propio formulario.
        if (proyecto.isAbierto()) {
            throw new ConflictoDeEstadoException("Los proyectos abiertos se editan desde Administración.");
        }

        proyecto.setNombre(dto.nombre());
        proyecto.setResumen(dto.resumen());
        proyecto.setDescripcion(dto.descripcion());
        proyecto.setUrlRepositorio(dto.urlRepositorio());
        proyecto.setSede(dto.sede());
        proyecto.setEstado(dto.estado());
        proyecto.setVisibilidad(dto.visibilidad());
        // Se reemplaza el contenido y no la lista, para que Hibernate siga el rastro de la
        // colección y sincronice sus tablas.
        if (dto.herramientas() != null) {
            proyecto.getHerramientas().clear();
            proyecto.getHerramientas().addAll(dto.herramientas());
        }
        sincronizarAdjuntos(proyecto, dto.adjuntos());
        // Los campos de moderación no se tocan: la dueña no puede revertir un ocultamiento.
        Proyecto guardado = repositorioProyectos.save(proyecto);
        publicar(TiposDeEvento.PROYECTO_ACTUALIZADO, usuarioId, null, guardado);
        return aRespuesta(guardado);
    }

    @Transactional
    public ProyectoRespuesta agregarColaborador(UUID id, String usuarioId, String nuevoColaboradorId) {
        Proyecto repo = repositorioProyectos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));

        if (!repo.getPropietarioId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException("Solo el propietario puede agregar colaboradores.");
        }
        if (repo.isAbierto()) {
            throw new ConflictoDeEstadoException("Los proyectos abiertos no tienen colaboradores.");
        }
        if (repo.getVisibilidad() != Visibilidad.COMPARTIDO) {
            throw new OperacionNoPermitidaException("Solo se pueden agregar colaboradores a proyectos compartidos");
        }
        boolean esNuevo = !repo.getColaboradoresIds().contains(nuevoColaboradorId);
        if (esNuevo) {
            repo.getColaboradoresIds().add(nuevoColaboradorId);
        }
        Proyecto guardado = repositorioProyectos.save(repo);
        if (esNuevo) {
            publicar(TiposDeEvento.PROYECTO_COLABORADOR_AGREGADO, usuarioId, nuevoColaboradorId, guardado);
        }
        return aRespuesta(guardado);
    }

    @Transactional
    public void eliminar(UUID id, String usuarioId) {
        Proyecto repo = repositorioProyectos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));

        if (!repo.getPropietarioId().equals(usuarioId)) {
            throw new OperacionNoPermitidaException("Solo el propietario puede eliminar este proyecto");
        }
        borrar(repo);
        publicar(TiposDeEvento.PROYECTO_ELIMINADO, usuarioId, null, repo);
    }

    /** Borra el proyecto y sus archivos. La moderación lo reutiliza sin la regla de dueño. */
    void borrar(Proyecto proyecto) {
        // Los archivos no se van solos con la fila: hay que sacarlos del bucket.
        proyecto.getAdjuntos().stream().filter(Adjunto::esArchivoSubido)
                .forEach(a -> servicioAdjuntos.borrar(a.getClaveS3()));
        repositorioProyectos.delete(proyecto);
    }

    /** El evento sale después del commit (ver {@code PublicacionTrasConfirmar}). */
    private void publicar(String tipo, String actorId, String destinatarioId, Proyecto proyecto) {
        String id = proyecto.getId().toString();
        eventos.publishEvent(EventoDuocConecta.nuevo(tipo, actorId, destinatarioId, id, id, null));
    }
}
