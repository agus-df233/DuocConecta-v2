-- Moderación (EP2): el rol ADMINISTRADOR puede ocultar un proyecto sin borrarlo.
-- Va aparte de `visibilidad` a propósito: la visibilidad la elige la persona dueña, y si el
-- administrador la cambiara a PRIVADO, la dueña podría volver a publicarlo con un simple PUT.

ALTER TABLE proyecto ADD COLUMN oculto              BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE proyecto ADD COLUMN motivo_ocultamiento VARCHAR(500);
-- Claim oid del administrador que lo ocultó.
ALTER TABLE proyecto ADD COLUMN ocultado_por        VARCHAR(100);
ALTER TABLE proyecto ADD COLUMN ocultado_en         TIMESTAMPTZ;

COMMENT ON COLUMN proyecto.oculto IS 'Oculto por moderación: solo lo ve su dueña y el administrador';

-- La vitrina filtra por visibilidad y excluye los ocultos en cada carga.
CREATE INDEX ix_proyecto_visibilidad_oculto ON proyecto (visibilidad, oculto);
