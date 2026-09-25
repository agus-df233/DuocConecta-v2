package cl.duoc.duocconecta.usuarios.controller;

import cl.duoc.duocconecta.comun.seguridad.ManejadorErroresBase;
import cl.duoc.duocconecta.usuarios.service.UsuarioNoEncontradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Errores propios de ms-usuarios. Los comunes vienen de {@link ManejadorErroresBase}. */
@RestControllerAdvice
public class ManejadorErrores extends ManejadorErroresBase {

    /** El perfil pedido no existe o su dueño lo ocultó. */
    @ExceptionHandler(UsuarioNoEncontradoException.class)
    public ProblemDetail manejarUsuarioNoEncontrado(UsuarioNoEncontradoException excepcion) {
        return problema(HttpStatus.NOT_FOUND, "Perfil no encontrado",
                "No se encontró un perfil visible con ese identificador.");
    }
}
