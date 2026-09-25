-- Creación de los schemas del monorepo.
-- Acá SOLO se crean los schemas vacíos: las tablas las crea Flyway desde cada
-- microservicio, así la definición del esquema vive junto al código que lo posee.

-- Schema de ms-usuarios: perfiles, roles y datos de contacto.
CREATE SCHEMA IF NOT EXISTS usuarios;

-- Schema de ms-proyectos: la vitrina de proyectos.
CREATE SCHEMA IF NOT EXISTS proyectos;

-- Schema de ms-contacto: solicitudes de colaboración y consentimiento.
CREATE SCHEMA IF NOT EXISTS contacto;
