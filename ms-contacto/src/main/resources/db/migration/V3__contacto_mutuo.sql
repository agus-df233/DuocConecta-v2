-- El intercambio de contacto pasa a ser mutuo, y las solicitudes se cuentan por proyecto.
--
-- Antes solo se guardaban los datos de quien aceptaba, así que quien pedía nunca compartía nada:
-- la otra persona aceptaba y no recibía forma de contactar a quien le había escrito.

-- 1. El nombre "compartido" no decía de quién eran. Siempre fueron los del solicitado.
ALTER TABLE solicitudes_contacto RENAME COLUMN correo_compartido   TO correo_solicitado;
ALTER TABLE solicitudes_contacto RENAME COLUMN telefono_compartido TO telefono_solicitado;
ALTER TABLE solicitudes_contacto RENAME COLUMN redes_compartidas   TO redes_solicitado;

-- 2. El lado que faltaba: lo que ofrece quien pide, al momento de pedir.
ALTER TABLE solicitudes_contacto ADD COLUMN correo_solicitante   VARCHAR(255);
ALTER TABLE solicitudes_contacto ADD COLUMN telefono_solicitante VARCHAR(50);
ALTER TABLE solicitudes_contacto ADD COLUMN redes_solicitante    VARCHAR(500);

-- 3. Una solicitud por proyecto, no por persona.
--
-- El índice anterior bloqueaba pedir contacto a alguien por un segundo proyecto suyo. Ahora la
-- unicidad incluye el proyecto y cubre también las aceptadas: si ya hay colaboración por ese
-- proyecto, no tiene sentido volver a pedirla.
DROP INDEX ux_solicitud_pendiente;

-- coalesce porque en Postgres dos NULL no chocan en un índice único, y proyecto_id es opcional.
CREATE UNIQUE INDEX ux_solicitud_por_proyecto
    ON solicitudes_contacto (solicitante_id, solicitado_id, COALESCE(proyecto_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE estado IN ('PENDIENTE', 'ACEPTADA');
