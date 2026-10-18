package cl.duoc.duocconecta.comun.seguridad;

import java.io.Serial;

/**
 * El token es válido pero no trae el correo. Sin correo no hay rol, así que se responde 403.
 *
 * <p>Con Entra ID, casi siempre faltan los claims opcionales del access token. Con Cognito, el cliente mandó el access token en vez del ID token, que es el que trae {@code email}.</p>
 */
public class CorreoNoPresenteException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CorreoNoPresenteException(java.util.List<String> claimsBuscados) {
        super("El token no trae el correo del usuario. Se buscó en los claims " + claimsBuscados
                + ". Con Entra ID, revisa los claims opcionales del access token; con Cognito, que el cliente envíe el ID token.");
    }
}
