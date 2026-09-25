package cl.duoc.duocconecta.usuarios.repository;

import cl.duoc.duocconecta.usuarios.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Acceso a los perfiles en el schema {@code usuarios}. */
@Repository
public interface RepositorioUsuarios extends JpaRepository<Usuario, UUID> {

    /** La que usa el auto-aprovisionamiento: el {@code oid} nunca cambia. */
    Optional<Usuario> findByOidEntra(String oidEntra);

    /** Busca el perfil por correo institucional. */
    Optional<Usuario> findByCorreo(String correo);

    /**
     * Los filtros nulos se ignoran, así el mismo query sirve para las cuatro combinaciones.
     * La comparación no distingue mayúsculas.
     */
    @Query("""
            SELECT u FROM Usuario u
            WHERE u.visible = true
              AND (:carrera IS NULL OR LOWER(u.carrera) = LOWER(:carrera))
              AND (:sede    IS NULL OR LOWER(u.sede)    = LOWER(:sede))
            ORDER BY u.nombre ASC
            """)
    Page<Usuario> buscarVisibles(@Param("carrera") String carrera,
                                 @Param("sede") String sede,
                                 Pageable paginacion);

    /** Para el perfil público: si la persona se ocultó, es como si no existiera. */
    Optional<Usuario> findByIdAndVisibleIsTrue(UUID id);

    /** Los demás microservicios guardan el {@code oid}, no el id interno. */
    Optional<Usuario> findByOidEntraAndVisibleIsTrue(String oidEntra);
}
