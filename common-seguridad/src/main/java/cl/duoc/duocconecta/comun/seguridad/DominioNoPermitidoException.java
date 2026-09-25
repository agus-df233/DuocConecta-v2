package cl.duoc.duocconecta.comun.seguridad;

import java.io.Serial;

/**
 * Se lanza cuando el correo del token no pertenece a ningún dominio institucional autorizado.
 *
 * <p>Es el caso de una cuenta externa (por ejemplo {@code @gmail.com}) que logró autenticarse
 * contra el tenant pero no debe tener perfil en la plataforma. Se traduce a un HTTP 403.</p>
 */
public class DominioNoPermitidoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Dominio que fue rechazado. Se guarda aparte para poder registrarlo sin exponer el correo completo. */
    private final transient String dominio;

    public DominioNoPermitidoException(String dominio) {
        super("El dominio '" + dominio + "' no pertenece a un correo institucional autorizado de Duoc UC.");
        this.dominio = dominio;
    }

    public String getDominio() {
        return dominio;
    }
}
