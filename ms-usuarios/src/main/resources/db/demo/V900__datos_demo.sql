-- Datos de ejemplo para la demo. SOLO se aplica con el perfil `dev`
-- (spring.flyway.locations incluye classpath:db/demo únicamente en ese perfil).
--
-- Sirve para que la vitrina y los filtros ?carrera= y ?sede= tengan algo que mostrar
-- antes de que exista ms-proyectos.
--
-- La versión 900 está a propósito lejos de las migraciones reales, para que estas
-- puedan seguir creciendo (V2, V3, ...) sin chocar con esta.

INSERT INTO usuario (id, oid_entra, nombre, correo, rol, carrera, sede, bio, visible, telefono) VALUES
    ('11111111-1111-1111-1111-111111111111', 'demo-oid-0001', 'Camila Rojas',
     'camila.rojas@duocuc.cl', 'ESTUDIANTE', 'Ingeniería en Informática', 'Plaza Oeste',
     'Me interesa el desarrollo backend y las bases de datos. Busco equipo para un proyecto de APIs.',
     TRUE, '+56 9 1111 1111'),

    ('22222222-2222-2222-2222-222222222222', 'demo-oid-0002', 'Matías Fuentes',
     'matias.fuentes@duocuc.cl', 'ESTUDIANTE', 'Diseño Gráfico', 'Antonio Varas',
     'Diseño de interfaces y sistemas visuales. Me gusta trabajar con gente de informática.',
     TRUE, NULL),

    ('33333333-3333-3333-3333-333333333333', 'demo-oid-0003', 'Valentina Soto',
     'valentina.soto@duocuc.cl', 'ESTUDIANTE', 'Ingeniería en Informática', 'San Joaquín',
     'Estudiando análisis de datos. Tengo un proyecto de visualización buscando colaboradores.',
     TRUE, NULL),

    ('44444444-4444-4444-4444-444444444444', 'demo-oid-0004', 'Ignacio Herrera',
     'ignacio.herrera@profesor.duoc.cl', 'PROFESOR', 'Ingeniería en Informática', 'Plaza Oeste',
     'Profesor de Desarrollo Cloud Native. Acompaño proyectos de arquitectura de microservicios.',
     TRUE, NULL),

    ('55555555-5555-5555-5555-555555555555', 'demo-oid-0005', 'Paula Contreras',
     'paula.contreras@duoc.cl', 'ACADEMICO', 'Dirección Académica', 'Casa Central',
     'Coordinación de vinculación entre carreras y sedes.',
     TRUE, NULL),

    -- Este perfil está oculto a propósito: sirve para comprobar que no aparece
    -- en el listado ni en GET /usuarios/{id}.
    ('66666666-6666-6666-6666-666666666666', 'demo-oid-0006', 'Diego Salazar',
     'diego.salazar@duocuc.cl', 'ESTUDIANTE', 'Ingeniería en Informática', 'Maipú',
     'Perfil oculto de prueba.',
     FALSE, NULL);

INSERT INTO usuario_redes (usuario_id, red) VALUES
    ('11111111-1111-1111-1111-111111111111', 'https://github.com/camilarojas'),
    ('11111111-1111-1111-1111-111111111111', 'https://linkedin.com/in/camilarojas'),
    ('22222222-2222-2222-2222-222222222222', 'https://behance.net/matiasfuentes'),
    ('33333333-3333-3333-3333-333333333333', 'https://github.com/valentinasoto');
