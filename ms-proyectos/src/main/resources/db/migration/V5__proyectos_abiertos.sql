-- Proyectos abiertos (EP2): los publica el administrador para que la vitrina tenga más proyectos a
-- la vista. Se pueden ver y comentar, pero no reciben solicitudes de colaboración.
--
-- `carrera` solo se usa en los abiertos: el resto de los proyectos toma la carrera del perfil de
-- quien lo publicó, pero un proyecto abierto se muestra como "Publicado por DuocConecta" y la
-- carrera la elige el administrador, para que los filtros de la vitrina sigan funcionando.

ALTER TABLE proyecto ADD COLUMN abierto BOOLEAN      NOT NULL DEFAULT FALSE;
ALTER TABLE proyecto ADD COLUMN carrera VARCHAR(120);

COMMENT ON COLUMN proyecto.abierto IS 'Publicado por el administrador: solo comentarios, sin solicitudes de colaboración';
