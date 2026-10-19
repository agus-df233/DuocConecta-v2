package cl.duoc.duocconecta.proyectos.service;

import cl.duoc.duocconecta.comun.eventos.EventoDuocConecta;
import cl.duoc.duocconecta.comun.eventos.TiposDeEvento;
import cl.duoc.duocconecta.proyectos.domain.Comentario;
import cl.duoc.duocconecta.proyectos.domain.Proyecto;
import cl.duoc.duocconecta.proyectos.domain.Visibilidad;
import cl.duoc.duocconecta.proyectos.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.proyectos.dto.EstadisticasProyectosRespuesta;
import cl.duoc.duocconecta.proyectos.dto.EstadisticasProyectosRespuesta.Conteo;
import cl.duoc.duocconecta.proyectos.dto.ProyectoAbiertoDatos;
import cl.duoc.duocconecta.proyectos.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.proyectos.exception.ConflictoDeEstadoException;
import cl.duoc.duocconecta.proyectos.exception.RecursoNoEncontradoException;
import cl.duoc.duocconecta.proyectos.repository.RepositorioComentarios;
import cl.duoc.duocconecta.proyectos.repository.RepositorioProyectos;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lo que hace el administrador sobre la vitrina: publicar proyectos abiertos, ocultar, mostrar y
 * borrar proyectos, y borrar comentarios. Vive aparte para no mezclar la moderación con las reglas
 * de la dueña.
 *
 * <p>Cada acción publica su evento: la dueña recibe un aviso y la auditoría lo registra.</p>
 */
@Service
@RequiredArgsConstructor
public class ServicioModeracion {

    private static final Logger log = LoggerFactory.getLogger(ServicioModeracion.class);

    private final RepositorioProyectos repositorioProyectos;
    private final RepositorioComentarios repositorioComentarios;
    private final ServicioProyectos servicioProyectos;
    private final ApplicationEventPublisher eventos;

    /** Todos, incluidos privados, compartidos y ocultos. Los filtros son opcionales. */
    @Transactional(readOnly = true)
    public List<ProyectoRespuesta> listar(Boolean oculto, Visibilidad visibilidad) {
        return repositorioProyectos.findAll().stream()
                .filter(p -> oculto == null || p.isOculto() == oculto)
                .filter(p -> visibilidad == null || p.getVisibilidad() == visibilidad)
                .sorted(Comparator.comparing(Proyecto::getFechaCreacion).reversed())
                .map(servicioProyectos::aRespuesta)
                .toList();
    }

    /** Totales y conteos de la vitrina para el dashboard. Incluye privados, compartidos y ocultos. */
    @Transactional(readOnly = true)
    public EstadisticasProyectosRespuesta estadisticas() {
        List<Proyecto> proyectos = repositorioProyectos.findAll();
        Instant hace30Dias = Instant.now().minus(Duration.ofDays(30));
        return new EstadisticasProyectosRespuesta(
                proyectos.size(),
                proyectos.stream().filter(p -> p.getVisibilidad() == Visibilidad.PUBLICO && !p.isOculto()).count(),
                proyectos.stream().filter(Proyecto::isOculto).count(),
                proyectos.stream().filter(p -> p.getFechaCreacion() != null && p.getFechaCreacion().isAfter(hace30Dias)).count(),
                repositorioComentarios.count(),
                contar(proyectos, p -> p.getVisibilidad() == null ? null : p.getVisibilidad().name()),
                contar(proyectos, p -> p.getEstado() == null ? null : p.getEstado().name()),
                contar(proyectos, Proyecto::getSede),
                contar(proyectos.stream().filter(p -> !p.isAbierto()).toList(), Proyecto::getPropietarioId),
                proyectos.stream().filter(Proyecto::isAbierto).count(),
                contar(proyectos.stream().filter(Proyecto::isAbierto).toList(), Proyecto::getCarrera));
    }

    /**
     * Publica un proyecto abierto: público, sin colaboradores y con la carrera que elige el
     * administrador. El dueño es el administrador, que queda registrado para la auditoría.
     */
    @Transactional
    public ProyectoRespuesta crearAbierto(ProyectoAbiertoDatos datos, String administradorId) {
        Proyecto proyecto = Proyecto.builder()
                .propietarioId(administradorId)
                .visibilidad(Visibilidad.PUBLICO)
                .abierto(true)
                .build();
        copiar(datos, proyecto);
        Proyecto guardado = repositorioProyectos.save(proyecto);
        log.info("Moderación: {} publicó el proyecto abierto {}", administradorId, guardado.getId());
        publicarAbierto(TiposDeEvento.PROYECTO_PUBLICADO, administradorId, guardado);
        return servicioProyectos.aRespuesta(guardado);
    }

    /** Edita un proyecto abierto. 404 si no existe; 409 si es un proyecto de la comunidad. */
    @Transactional
    public ProyectoRespuesta editarAbierto(UUID id, ProyectoAbiertoDatos datos, String administradorId) {
        Proyecto proyecto = buscar(id);
        if (!proyecto.isAbierto()) {
            throw new ConflictoDeEstadoException(
                    "Solo se editan aquí los proyectos abiertos: los de la comunidad los edita su dueña.");
        }
        copiar(datos, proyecto);
        Proyecto guardado = repositorioProyectos.save(proyecto);
        log.info("Moderación: {} editó el proyecto abierto {}", administradorId, id);
        publicarAbierto(TiposDeEvento.PROYECTO_ACTUALIZADO, administradorId, guardado);
        return servicioProyectos.aRespuesta(guardado);
    }

    /** Pasa los datos del formulario al proyecto. Las listas se reemplazan por dentro para que Hibernate las siga. */
    private void copiar(ProyectoAbiertoDatos datos, Proyecto proyecto) {
        proyecto.setNombre(datos.nombre());
        proyecto.setResumen(datos.resumen());
        proyecto.setDescripcion(datos.descripcion());
        proyecto.setUrlRepositorio(datos.urlRepositorio());
        proyecto.setSede(datos.sede());
        proyecto.setCarrera(datos.carrera().trim());
        proyecto.setEstado(datos.estado());
        proyecto.getHerramientas().clear();
        if (datos.herramientas() != null) {
            proyecto.getHerramientas().addAll(datos.herramientas());
        }
        servicioProyectos.sincronizarAdjuntos(proyecto, datos.adjuntos());
    }

    /** Agrupa por la clave dada, de mayor a menor. Una clave vacía cuenta como "Sin completar". */
    private static List<Conteo> contar(List<Proyecto> proyectos, Function<Proyecto, String> clave) {
        Map<String, Long> conteo = proyectos.stream().collect(Collectors.groupingBy(
                p -> {
                    String valor = clave.apply(p);
                    return valor == null || valor.isBlank() ? "Sin completar" : valor;
                },
                Collectors.counting()));
        return conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Conteo(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public ProyectoRespuesta ocultar(UUID id, String motivo, String administradorId) {
        Proyecto proyecto = buscar(id);
        if (proyecto.isOculto()) {
            throw new ConflictoDeEstadoException("El proyecto ya está oculto.");
        }
        proyecto.ocultar(motivo.trim(), administradorId);
        Proyecto guardado = repositorioProyectos.save(proyecto);
        log.info("Moderación: {} ocultó el proyecto {}", administradorId, id);
        publicar(TiposDeEvento.PROYECTO_OCULTADO, administradorId, guardado, guardado.getId(), guardado.getMotivoOcultamiento());
        return servicioProyectos.aRespuesta(guardado);
    }

    @Transactional
    public ProyectoRespuesta mostrar(UUID id, String administradorId) {
        Proyecto proyecto = buscar(id);
        if (!proyecto.isOculto()) {
            throw new ConflictoDeEstadoException("El proyecto no está oculto.");
        }
        proyecto.mostrar();
        Proyecto guardado = repositorioProyectos.save(proyecto);
        log.info("Moderación: {} volvió a mostrar el proyecto {}", administradorId, id);
        publicar(TiposDeEvento.PROYECTO_RESTAURADO, administradorId, guardado, guardado.getId(), null);
        return servicioProyectos.aRespuesta(guardado);
    }

    /** Borra cualquier proyecto con sus archivos, sin la regla de dueño. */
    @Transactional
    public void eliminar(UUID id, String administradorId) {
        Proyecto proyecto = buscar(id);
        servicioProyectos.borrar(proyecto);
        log.info("Moderación: {} eliminó el proyecto {}", administradorId, id);
        publicar(TiposDeEvento.PROYECTO_ELIMINADO, administradorId, proyecto, proyecto.getId(), "moderacion");
    }

    /** Los comentarios de un proyecto, aunque sea privado u oculto; sin proyecto, los 50 más recientes. */
    @Transactional(readOnly = true)
    public List<ComentarioRespuesta> comentarios(UUID proyectoId) {
        List<Comentario> comentarios = proyectoId == null
                ? repositorioComentarios.findTop50ByOrderByFechaDesc()
                : repositorioComentarios.findByProyectoIdOrderByFechaAsc(proyectoId);
        return comentarios.stream().map(ComentarioRespuesta::desdeEntidad).toList();
    }

    @Transactional
    public void eliminarComentario(UUID id, String administradorId) {
        Comentario comentario = repositorioComentarios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Comentario no encontrado: " + id));
        repositorioComentarios.delete(comentario);
        log.info("Moderación: {} eliminó el comentario {}", administradorId, id);
        // El aviso va a quien escribió el comentario, no a la dueña del proyecto.
        eventos.publishEvent(EventoDuocConecta.nuevo(TiposDeEvento.PROYECTO_COMENTARIO_ELIMINADO,
                administradorId, comentario.getAutorId(), id.toString(),
                comentario.getProyectoId().toString(), null));
    }

    private Proyecto buscar(UUID id) {
        return repositorioProyectos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Proyecto no encontrado: " + id));
    }

    /** Un proyecto abierto no tiene a quién avisar: el evento va sin destinatario, solo para la auditoría. */
    private void publicarAbierto(String tipo, String administradorId, Proyecto proyecto) {
        String id = proyecto.getId().toString();
        eventos.publishEvent(EventoDuocConecta.nuevo(tipo, administradorId, null, id, id, null));
    }

    /** El aviso va a la dueña del proyecto. */
    private void publicar(String tipo, String administradorId, Proyecto proyecto, UUID id, String detalle) {
        eventos.publishEvent(EventoDuocConecta.nuevo(tipo, administradorId, proyecto.getPropietarioId(),
                id.toString(), id.toString(), detalle));
    }
}
