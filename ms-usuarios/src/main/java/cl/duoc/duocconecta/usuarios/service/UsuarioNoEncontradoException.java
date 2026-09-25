package cl.duoc.duocconecta.usuarios.service;

import java.io.Serial;
import java.util.UUID;

/**
 * Se lanza cuando el perfil pedido no existe o está oculto.
 *
 * <p>Los dos casos se tratan igual a propósito: si una persona se ocultó, responder "existe pero
 * está oculto" delataría su presencia en la plataforma. Se traduce a un HTTP 404.</p>
 */
public class UsuarioNoEncontradoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UsuarioNoEncontradoException(UUID id) {
        super("No existe un perfil visible con el identificador " + id + ".");
    }

    /** Cuando la búsqueda fue por el identificador de Azure AD y no por el id interno. */
    public UsuarioNoEncontradoException(String oid) {
        super("No existe un perfil visible para el usuario " + oid + ".");
    }
}
