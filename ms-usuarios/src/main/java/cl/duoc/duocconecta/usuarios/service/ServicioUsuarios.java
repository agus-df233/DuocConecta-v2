package cl.duoc.duocconecta.usuarios.service;

import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import cl.duoc.duocconecta.usuarios.domain.Usuario;
import cl.duoc.duocconecta.usuarios.dto.ActualizarPerfilDatos;
import cl.duoc.duocconecta.usuarios.dto.PerfilPrivadoRespuesta;
import cl.duoc.duocconecta.usuarios.dto.PerfilPublicoRespuesta;
import cl.duoc.duocconecta.usuarios.repository.RepositorioUsuarios;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Perfiles de usuario.
 *
 * <p>La identidad sale del token vía {@link UsuarioActual}: ningún método recibe el {@code oid}
 * por parámetro. Se devuelven DTOs y no entidades porque las redes se cargan de forma perezosa y
 * fuera de la transacción ya no hay sesión con la base.</p>
 *
 * <p>Las transacciones van con {@link TransactionTemplate} y no con anotaciones porque el
 * auto-aprovisionamiento necesita reintentar en una transacción <em>nueva</em>.</p>
 */
@Service
public class ServicioUsuarios {

    private static final Logger log = LoggerFactory.getLogger(ServicioUsuarios.class);

    private final RepositorioUsuarios repositorio;
    private final UsuarioActual usuarioActual;
    private final TransactionTemplate transaccion;

    public ServicioUsuarios(RepositorioUsuarios repositorio,
                          UsuarioActual usuarioActual,
                          TransactionTemplate transaccion) {
        this.repositorio = repositorio;
        this.usuarioActual = usuarioActual;
        this.transaccion = transaccion;
    }

    /** Auto-aprovisionamiento: no hay registro manual. Un dominio no autorizado no crea perfil. */
    public PerfilPrivadoRespuesta obtenerOCrearPerfilPropio() {
        return sobreMiPerfil(PerfilPrivadoRespuesta::desde);
    }

    public PerfilPrivadoRespuesta actualizarPerfilPropio(ActualizarPerfilDatos solicitud) {
        return sobreMiPerfil(usuario -> {
            usuario.actualizarPerfil(
                    solicitud.carrera(),
                    solicitud.sede(),
                    solicitud.bio(),
                    solicitud.telefono(),
                    solicitud.redes());
            return PerfilPrivadoRespuesta.desde(usuario);
        });
    }

    /** @return el nuevo valor de visibilidad */
    public boolean alternarVisibilidadPropia() {
        return sobreMiPerfil(Usuario::alternarVisibilidad);
    }

    /** Solo las propias: las de terceros quedan sujetas al consentimiento. */
    public List<String> obtenerRedesPropias() {
        return sobreMiPerfil(usuario -> List.copyOf(usuario.getRedes()));
    }

    /** @throws UsuarioNoEncontradoException si no existe o está oculto */
    public PerfilPublicoRespuesta buscarPerfilPublico(UUID id) {
        return transaccion.execute(estado -> repositorio.findByIdAndVisibleIsTrue(id)
                .map(PerfilPublicoRespuesta::desde)
                .orElseThrow(() -> new UsuarioNoEncontradoException(id)));
    }

    /**
     * El resto de la plataforma identifica a las personas por el {@code oid}, no por el id interno.
     *
     * @throws UsuarioNoEncontradoException si no existe o está oculto
     */
    public PerfilPublicoRespuesta buscarPerfilPublicoPorOid(String oid) {
        return transaccion.execute(estado -> repositorio.findByOidEntraAndVisibleIsTrue(oid)
                .map(PerfilPublicoRespuesta::desde)
                .orElseThrow(() -> new UsuarioNoEncontradoException(oid)));
    }

    public Page<PerfilPublicoRespuesta> listarPerfilesVisibles(String carrera, String sede,
                                                              Pageable paginacion) {
        return transaccion.execute(estado -> repositorio
                .buscarVisibles(vacioComoNulo(carrera), vacioComoNulo(sede), paginacion)
                .map(PerfilPublicoRespuesta::desde));
    }

    /** Se llama siempre desde una transacción abierta por quien invoca. */
    private Usuario obtenerOCrear(UsuarioActual.IdentidadUsuario identidad) {
        return repositorio.findByOidEntra(identidad.oid())
                .map(existente -> sincronizar(existente, identidad))
                .orElseGet(() -> crearPerfil(identidad));
    }

    /**
     * Dos peticiones simultáneas del mismo login chocan contra el índice único de
     * {@code oid_entra}. PostgreSQL deja abortada esa transacción, así que se reintenta en una nueva.
     */
    private Usuario crearPerfil(UsuarioActual.IdentidadUsuario identidad) {
        Usuario nuevo = new Usuario(
                identidad.oid(), identidad.nombre(), identidad.correo(), identidad.rol());
        // saveAndFlush: que el choque salte aquí y no más tarde, sin saber quién lo provocó.
        Usuario guardado = repositorio.saveAndFlush(nuevo);
        log.info("Perfil auto-aprovisionado para el rol {} en el dominio {}.",
                identidad.rol(), dominioDe(identidad.correo()));
        return guardado;
    }

    /**
     * Opera sobre el perfil propio dentro de una transacción, creándolo si es el primer ingreso.
     * Si dos peticiones chocan al crearlo, el reintento va en una transacción nueva.
     */
    private <T> T sobreMiPerfil(java.util.function.Function<Usuario, T> accion) {
        UsuarioActual.IdentidadUsuario identidad = usuarioActual.obtener();
        try {
            return transaccion.execute(estado -> accion.apply(obtenerOCrear(identidad)));
        } catch (DataIntegrityViolationException creacionSimultanea) {
            log.debug("El perfil lo creó otra petición simultánea; se reintenta.");
            return transaccion.execute(estado -> accion.apply(obtenerOCrear(identidad)));
        }
    }

    /** Refresca lo que viene del token, por si cambió en Azure AD. */
    private Usuario sincronizar(Usuario existente, UsuarioActual.IdentidadUsuario identidad) {
        existente.sincronizarDesdeToken(identidad.nombre(), identidad.correo(), identidad.rol());
        return existente;
    }

    /** Convierte los filtros vacíos o en blanco a nulo. */
    private static String vacioComoNulo(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.trim();
    }

    /** Saca el dominio del correo para registrarlo en el log sin exponer el correo entero. */
    private static String dominioDe(String correo) {
        int posicionArroba = correo.lastIndexOf('@');
        return posicionArroba >= 0 ? correo.substring(posicionArroba + 1) : "desconocido";
    }
}
