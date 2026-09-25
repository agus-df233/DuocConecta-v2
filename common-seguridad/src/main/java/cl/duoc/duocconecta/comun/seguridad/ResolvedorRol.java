package cl.duoc.duocconecta.comun.seguridad;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce el dominio del correo al rol. Es el único lugar donde vive esa regla.
 *
 * <p>Los dominios salen de {@code duocconecta.seguridad.dominios}: sumar uno es editar el YAML.</p>
 */
public class ResolvedorRol {

    private final Map<String, Rol> dominiosNormalizados;

    public ResolvedorRol(PropiedadesSeguridad propiedades) {
        Map<String, Rol> normalizados = new LinkedHashMap<>();
        propiedades.getDominios().forEach((dominio, rol) ->
                normalizados.put(normalizar(dominio), rol));
        this.dominiosNormalizados = Map.copyOf(normalizados);
    }

    /** Vacío si el dominio no está autorizado. */
    public Optional<Rol> resolverPorCorreo(String correo) {
        return extraerDominio(correo).flatMap(this::resolverPorDominio);
    }

    /** El dominio va sin arroba: {@code duocuc.cl}. */
    public Optional<Rol> resolverPorDominio(String dominio) {
        if (dominio == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dominiosNormalizados.get(normalizar(dominio)));
    }

    /** Todo lo que sigue al ÚLTIMO arroba: la parte local puede contener uno entre comillas. */
    public Optional<String> extraerDominio(String correo) {
        if (correo == null || correo.isBlank()) {
            return Optional.empty();
        }
        int posicionArroba = correo.lastIndexOf('@');
        // Sin arroba, sin parte local o sin dominio: no es un correo.
        if (posicionArroba <= 0 || posicionArroba == correo.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(normalizar(correo.substring(posicionArroba + 1)));
    }

    /**
     * La comparación es por igualdad exacta, nunca por sufijo: con {@code endsWith},
     * {@code @profesor.duoc.cl} también coincidiría con {@code duoc.cl}.
     */
    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(Locale.ROOT);
    }
}
