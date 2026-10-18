package cl.duoc.duocconecta.comun.seguridad;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Traduce el dominio del correo al rol. Es el único lugar donde vive esa regla.
 *
 * <p>Los dominios salen de {@code duocconecta.seguridad.dominios}: sumar uno es editar el YAML.
 * Un correo de {@code duocconecta.seguridad.correos-permitidos} (una cuenta de Google, por ejemplo)
 * tiene prioridad sobre su dominio.</p>
 */
public class ResolvedorRol {

    private final Map<String, Rol> dominiosNormalizados;
    private final Map<String, Rol> correosPermitidos;

    public ResolvedorRol(PropiedadesSeguridad propiedades) {
        Map<String, Rol> normalizados = new LinkedHashMap<>();
        propiedades.getDominios().forEach((dominio, rol) ->
                normalizados.put(normalizar(dominio), rol));
        this.dominiosNormalizados = Map.copyOf(normalizados);
        this.correosPermitidos = leerCorreosPermitidos(propiedades);
    }

    /** Vacío si ni el correo ni su dominio están autorizados. */
    public Optional<Rol> resolverPorCorreo(String correo) {
        Rol permitido = correo == null ? null : correosPermitidos.get(normalizar(correo));
        if (permitido != null) {
            return Optional.of(permitido);
        }
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
     * Lee las entradas {@code correo=ROL}. Una mal escrita detiene el arranque: fallar callado
     * dejaría a alguien sin acceso sin que nadie sepa por qué.
     */
    private static Map<String, Rol> leerCorreosPermitidos(PropiedadesSeguridad propiedades) {
        Map<String, Rol> permitidos = new LinkedHashMap<>();
        for (String entrada : propiedades.getCorreosPermitidos()) {
            if (entrada == null || entrada.isBlank()) {
                continue;
            }
            int separador = entrada.lastIndexOf('=');
            String correo = separador > 0 ? normalizar(entrada.substring(0, separador)) : "";
            String nombreRol = separador > 0 ? entrada.substring(separador + 1).trim().toUpperCase(Locale.ROOT) : "";
            if (correo.indexOf('@') <= 0 || nombreRol.isEmpty()) {
                throw new IllegalStateException("Entrada inválida en correos-permitidos: '" + entrada
                        + "'. Usa la forma correo=ROL, por ejemplo ana@gmail.com=ESTUDIANTE");
            }
            Rol rol;
            try {
                rol = Rol.valueOf(nombreRol);
            } catch (IllegalArgumentException desconocido) {
                throw new IllegalStateException("Rol desconocido en correos-permitidos: '" + entrada + "'");
            }
            // El administrador se suma aparte (lista administradores o grupo del IdP), nunca reemplaza al rol base.
            if (rol == Rol.ADMINISTRADOR) {
                throw new IllegalStateException("correos-permitidos no acepta ADMINISTRADOR ('" + entrada
                        + "'): usa la lista administradores");
            }
            permitidos.put(correo, rol);
        }
        return Map.copyOf(permitidos);
    }

    /**
     * La comparación es por igualdad exacta, nunca por sufijo: con {@code endsWith},
     * {@code @profesor.duoc.cl} también coincidiría con {@code duoc.cl}.
     */
    private static String normalizar(String valor) {
        return valor == null ? null : valor.trim().toLowerCase(Locale.ROOT);
    }
}
