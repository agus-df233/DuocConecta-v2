package cl.duoc.duocconecta.comun.seguridad;

import java.io.Serial;

/**
 * El token es válido pero no trae el correo. Sin correo no hay rol, así que se responde 403.
 *
 * <p>Casi siempre faltan los claims opcionales del access token en el registro de Azure AD.</p>
 */
public class CorreoNoPresenteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CorreoNoPresenteException(java.util.List<String> claimsBuscados) {
        super("El token no trae el correo del usuario. Se buscó en los claims " + claimsBuscados
                + ". Revisa los claims opcionales del access token en el registro de la app en Azure AD.");
    }
}
