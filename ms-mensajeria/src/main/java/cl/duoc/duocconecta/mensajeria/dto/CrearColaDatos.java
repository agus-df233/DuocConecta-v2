package cl.duoc.duocconecta.mensajeria.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo para crear una cola. Los rangos son los que admite SQS; lo que no se indica toma el
 * valor de la topología base.
 */
@Schema(description = "Nueva cola SQS, opcionalmente con su DLQ")
public record CrearColaDatos(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = ReglasDeNombres.LARGO_MAXIMO, message = "El nombre admite hasta 69 caracteres (la DLQ suma -descartes)")
        @Pattern(regexp = ReglasDeNombres.PATRON, message = ReglasDeNombres.MENSAJE)
        @Schema(example = "duocconecta-prueba")
        String nombre,

        @Min(value = 60, message = "La retención mínima de SQS es 60 segundos")
        @Max(value = 1_209_600, message = "La retención máxima de SQS es 14 días (1209600 s)")
        @Schema(description = "Segundos; por defecto 4 días", example = "345600")
        Integer retencionSegundos,

        @Min(value = 0, message = "La visibilidad no puede ser negativa")
        @Max(value = 43_200, message = "La visibilidad máxima de SQS es 12 horas (43200 s)")
        @Schema(description = "Segundos que un mensaje queda oculto mientras se procesa", example = "30")
        Integer visibilidadSegundos,

        @Min(value = 0, message = "La espera no puede ser negativa")
        @Max(value = 20, message = "La espera máxima de SQS (long polling) es 20 segundos")
        @Schema(example = "20")
        Integer esperaSegundos,

        @Schema(description = "Crear también la DLQ <nombre>-descartes con su redrive; por defecto sí")
        Boolean conDescarte,

        @Min(value = 1, message = "Se necesita al menos 1 recepción antes de la DLQ")
        @Max(value = 1000, message = "SQS admite hasta 1000 recepciones antes de la DLQ")
        @Schema(description = "Entregas sin confirmar antes de mover a la DLQ", example = "3")
        Integer maxRecepciones,

        @Min(value = 60, message = "La retención mínima de SQS es 60 segundos")
        @Max(value = 1_209_600, message = "La retención máxima de SQS es 14 días (1209600 s)")
        @Schema(description = "Retención de la DLQ; por defecto 14 días", example = "1209600")
        Integer retencionDescarteSegundos) {

    public boolean quiereDescarte() {
        return conDescarte == null || conDescarte;
    }

    /**
     * En SQS lo vencido se borra, y en colas estándar la antigüedad cuenta desde que el mensaje
     * entró a la cola original: la DLQ tiene que retener más que su cola.
     */
    @AssertTrue(message = "La DLQ tiene que retener más tiempo que su cola")
    @Schema(hidden = true)
    public boolean isRetencionDeDescarteCoherente() {
        if (!quiereDescarte() || retencionSegundos == null || retencionDescarteSegundos == null) {
            return true;
        }
        return retencionDescarteSegundos > retencionSegundos;
    }

    /** Sin DLQ, pedir recepciones no tiene sentido. */
    @AssertTrue(message = "maxRecepciones solo aplica si la cola tiene DLQ")
    @Schema(hidden = true)
    public boolean isRecepcionesConDescarte() {
        return quiereDescarte() || maxRecepciones == null;
    }
}
