package cl.duoc.duocconecta.contacto.dto;

import jakarta.validation.constraints.NotNull;

public record DecisionSolicitud(

        @NotNull(message = "Debes indicar si aceptas la solicitud")
        boolean aceptar,

        /**
         * Datos que se comparten, y que solo se usan si aceptar = true.
         *
         * <p>Los arma el BFF con el perfil de quien acepta, así nadie tiene que escribir su
         * propio correo a mano. Los que lleguen vacíos simplemente no se comparten.</p>
         */
        String correo,
        String telefono,
        String redes
) {
}
