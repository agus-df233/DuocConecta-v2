-- Completa los proyectos de ejemplo con el resumen y las herramientas que suma V2.
-- Va aparte y no dentro de V900 porque esa migración ya se aplicó: cambiarla rompería
-- la suma de verificación que Flyway guarda.

UPDATE proyecto SET resumen = 'Reserva de salas de estudio por sede, con API propia.'
 WHERE id = 'aaaaaaaa-0000-0000-0000-000000000001';
UPDATE proyecto SET resumen = 'Piezas gráficas para la feria de carreras: afiches, redes y señalética.'
 WHERE id = 'aaaaaaaa-0000-0000-0000-000000000002';
UPDATE proyecto SET resumen = 'Panel que muestra la asistencia por sección en tiempo real.'
 WHERE id = 'aaaaaaaa-0000-0000-0000-000000000003';
UPDATE proyecto SET resumen = 'Borrador privado, todavía sin publicar.'
 WHERE id = 'aaaaaaaa-0000-0000-0000-000000000004';

-- Herramientas variadas a propósito: no todo en la plataforma es desarrollo.
INSERT INTO proyecto_herramientas (proyecto_id, herramienta) VALUES
    ('aaaaaaaa-0000-0000-0000-000000000001', 'Spring Boot'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'PostgreSQL'),
    ('aaaaaaaa-0000-0000-0000-000000000001', 'React'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Figma'),
    ('aaaaaaaa-0000-0000-0000-000000000002', 'Illustrator'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Power BI'),
    ('aaaaaaaa-0000-0000-0000-000000000003', 'Excel');
