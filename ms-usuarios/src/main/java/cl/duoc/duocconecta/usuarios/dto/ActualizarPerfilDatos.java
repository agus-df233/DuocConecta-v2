package cl.duoc.duocconecta.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Datos que la persona puede modificar de su propio perfil.
 *
 * <p>Son exactamente los datos que el login NO puede aprovisionar solo. El nombre, el correo, el
 * rol y el identificador de Azure AD no están aquí a propósito: vienen del token, y el nombre además
 * se resincroniza en cada ingreso, así que dejarlo editable haría que el valor escrito a mano se
 * perdiera en el siguiente login.</p>
 *
 * @param carrera  carrera a la que pertenece
 * @param sede     sede en la que estudia o trabaja
 * @param bio      presentación breve para la vitrina
 * @param telefono teléfono de contacto (privado)
 * @param redes    enlaces a redes sociales (privados)
 */
@Schema(description = "Campos editables del perfil propio")
public record ActualizarPerfilDatos(

        @Size(max = 120, message = "La carrera no puede superar los 120 caracteres.")
        @Schema(description = "Carrera", example = "Ingeniería en Informática")
        String carrera,

        @Size(max = 120, message = "La sede no puede superar los 120 caracteres.")
        @Schema(description = "Sede", example = "Plaza Oeste")
        String sede,

        @Size(max = 500, message = "La biografía no puede superar los 500 caracteres.")
        @Schema(description = "Presentación breve que se muestra en la vitrina")
        String bio,

        // Se acepta un formato amplio a propósito: teléfonos con o sin código de país,
        // con espacios o guiones. Solo se rechaza lo que claramente no es un teléfono.
        @Pattern(regexp = "^$|^[+]?[0-9\\s().-]{6,25}$",
                message = "El teléfono solo puede tener números, espacios, guiones, paréntesis y un + inicial.")
        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
        @Schema(description = "Teléfono de contacto (dato privado)", example = "+56 9 1234 5678")
        String telefono,

        @Size(max = 10, message = "No se pueden guardar más de 10 redes sociales.")
        @Schema(description = "Enlaces a redes sociales (datos privados)",
                example = "[\"https://github.com/juanaperez\"]")
        List<@Size(max = 255, message = "Cada red no puede superar los 255 caracteres.") String> redes) {
}
