package cl.duoc.duocconecta.usuarios.service;

import java.io.Serial;

/**
 * El correo del token ya tiene un perfil creado con otro proveedor de inicio de sesión.
 *
 * <p>Microsoft y Google (vía Cognito) entregan identificadores distintos para la misma persona, y
 * todo en la plataforma se guarda con ese identificador. Unir los dos dejaría los proyectos y las
 * solicitudes repartidos entre dos identidades, así que se rechaza con un HTTP 409.</p>
 */
public class CorreoConOtraIdentidadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CorreoConOtraIdentidadException() {
        super("El correo ya tiene un perfil creado con otro proveedor de inicio de sesión.");
    }
}
