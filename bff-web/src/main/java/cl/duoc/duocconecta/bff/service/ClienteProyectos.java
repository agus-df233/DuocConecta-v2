package cl.duoc.duocconecta.bff.service;

import cl.duoc.duocconecta.bff.dto.ComentarioDatos;
import cl.duoc.duocconecta.bff.dto.ComentarioRespuesta;
import cl.duoc.duocconecta.bff.dto.ProyectoRespuesta;
import cl.duoc.duocconecta.comun.seguridad.UsuarioActual;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente hacia ms-proyectos.
 *
 * <p>Publicar, editar y borrar los hace el frontend directo: no hay nada que componer. Listar y
 * comentar pasan por aquí para sumarles quién publicó y quién comentó.</p>
 */
@Service
public class ClienteProyectos {

    private static final String RUTA = "/api/v1/proyectos";

    private static final ParameterizedTypeReference<List<ProyectoRespuesta>> LISTA =
            new ParameterizedTypeReference<>() {};

    private static final ParameterizedTypeReference<List<ComentarioRespuesta>> COMENTARIOS =
            new ParameterizedTypeReference<>() {};

    private final RestClient cliente;
    private final UsuarioActual usuarioActual;

    public ClienteProyectos(RestClient clienteMsProyectos, UsuarioActual usuarioActual) {
        this.cliente = clienteMsProyectos;
        this.usuarioActual = usuarioActual;
    }

    /**
     * Nombre de un proyecto, para dar contexto a una solicitud de colaboración.
     *
     * <p>Devuelve null si el proyecto no existe o si quien pregunta no puede verlo. Que un
     * proyecto se haya borrado no es motivo para que falle la bandeja entera.</p>
     */
    public String nombreDe(UUID id) {
        try {
            ProyectoRespuesta proyecto = cliente.get()
                    .uri(RUTA + "/{id}", id)
                    .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                    .retrieve()
                    .body(ProyectoRespuesta.class);
            return proyecto == null ? null : proyecto.nombre();
        } catch (HttpClientErrorException ignorada) {
            return null;
        }
    }

    /** Proyectos que la persona autenticada tiene permitido ver. */
    public List<ProyectoRespuesta> listar() {
        List<ProyectoRespuesta> respuesta = cliente.get()
                .uri(RUTA)
                .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                .retrieve()
                .body(LISTA);
        return respuesta == null ? List.of() : respuesta;
    }

    /** Hilo de comentarios de un proyecto. */
    public List<ComentarioRespuesta> comentarios(UUID proyectoId) {
        List<ComentarioRespuesta> respuesta = cliente.get()
                .uri(RUTA + "/{id}/comentarios", proyectoId)
                .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                .retrieve()
                .body(COMENTARIOS);
        return respuesta == null ? List.of() : respuesta;
    }

    /** Publica un comentario en un proyecto. */
    public ComentarioRespuesta comentar(UUID proyectoId, ComentarioDatos comentario) {
        return cliente.post()
                .uri(RUTA + "/{id}/comentarios", proyectoId)
                .header(HttpHeaders.AUTHORIZATION, usuarioActual.cabeceraAuthorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(comentario)
                .retrieve()
                .body(ComentarioRespuesta.class);
    }
}
