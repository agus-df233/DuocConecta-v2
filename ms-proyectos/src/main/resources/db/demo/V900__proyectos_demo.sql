-- Proyectos de ejemplo. SOLO se aplica con el perfil `dev`.
-- Los propietarios coinciden con los perfiles demo de ms-usuarios, así que en la vitrina
-- cada proyecto aparece con un autor que existe.
-- La versión 900 está lejos de las migraciones reales para que estas puedan seguir creciendo.

INSERT INTO proyecto (id, nombre, descripcion, url_repositorio, propietario_id, sede, estado, visibilidad) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Sistema de reserva de salas',
     'API en Spring Boot para reservar salas de estudio por sede. Busco alguien de diseño para la interfaz.',
     'https://github.com/ejemplo/reserva-salas', 'demo-oid-0001', 'Plaza Oeste', 'BUSCANDO_EQUIPO', 'PUBLICO'),

    ('aaaaaaaa-0000-0000-0000-000000000002', 'Identidad visual para la feria de carreras',
     'Sistema de piezas gráficas: afiches, redes y señalética. Abierto a sumar gente.',
     NULL, 'demo-oid-0002', 'Antonio Varas', 'EN_DESARROLLO', 'PUBLICO'),

    ('aaaaaaaa-0000-0000-0000-000000000003', 'Panel de asistencia en tiempo real',
     'Visualización de asistencia por sección, con datos de ejemplo. Terminado y documentado.',
     'https://github.com/ejemplo/panel-asistencia', 'demo-oid-0003', 'San Joaquín', 'TERMINADO', 'PUBLICO'),

    -- Este es privado a propósito: sirve para comprobar que no aparece en la vitrina ajena.
    ('aaaaaaaa-0000-0000-0000-000000000004', 'Borrador de tesis',
     'Proyecto personal todavía sin publicar.',
     NULL, 'demo-oid-0001', 'Plaza Oeste', 'EN_DESARROLLO', 'PRIVADO');

INSERT INTO proyecto_archivos (proyecto_id, url_archivo) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000003', 'https://ejemplo.cl/panel-asistencia/capturas.pdf');
