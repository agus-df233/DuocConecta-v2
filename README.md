# DuocConecta

**Una vitrina de proyectos y una red de contactos para la comunidad de Duoc UC, sede Viña del Mar.**

---

## Para qué sirve

Hoy lo que hace la comunidad de Duoc vive disperso: un proyecto excelente termina en un grupo de
WhatsApp que nadie vuelve a abrir, o en el Drive personal de quien lo hizo. Cada semestre se pierde,
no cruza entre carreras y quien llega nuevo empieza de cero.

DuocConecta junta ese trabajo en un solo lugar y permite que las personas se encuentren:

- **Publica lo que estás haciendo.** Un proyecto de título, un trabajo de asignatura, algo que
  armaste por tu cuenta. Con su descripción, las herramientas que usaste y en qué estado va.
- **Mira lo que hacen otras carreras.** La vitrina se filtra por escuela, carrera, herramienta y
  estado. Alguien de Diseño puede encontrar a quien necesita para su idea en Ingeniería en Sonido.
- **Comenta y pide colaborar.** Si un proyecto te interesa, puedes escribirle a quien lo publicó.

## La promesa sobre tus datos

**Nadie ve tu correo ni tu teléfono hasta que tú aceptas.** Esta es la regla central de la
plataforma, no una opción escondida en la configuración.

Cuando alguien quiere contactarte, te llega una solicitud con su mensaje. Ni tus datos ni los suyos
viajan en ese momento. **Recién si tú aceptas se comparten, y en los dos sentidos**: pedir contacto
es ofrecer el propio. Cada uno decide si incluye su teléfono. Si rechazas, esa persona nunca ve nada
tuyo y tú no ves nada suyo.

Comentar un proyecto tampoco comparte datos de contacto. Y puedes ocultar tu perfil de las
búsquedas cuando quieras, desde tu propio perfil.

## Cómo entrar

Con tu **cuenta institucional de Duoc**. No hay que crear una contraseña nueva: DuocConecta no
almacena contraseñas, la autenticación la hace Microsoft con la misma cuenta que ya usas.

Tu rol sale del dominio de tu correo:

| Si tu correo es | Entras como |
|---|---|
| `@duocuc.cl` | Estudiante |
| `@profesor.duoc.cl` | Profesor |
| `@duoc.cl` | Académico |

Una cuenta que no sea de Duoc no puede entrar, y tampoco se le crea un perfil.

---

# Parte técnica

Asignatura **DSY1107 · Desarrollo Cloud Native I** — Evaluación Parcial 1, que evalúa
**autenticación con Azure AD (Entra ID)**, **validación de JWT en cada capa** y **despliegue en la
nube**.

- Arquitectura, convenciones y decisiones fijas → [`CLAUDE.md`](CLAUDE.md)

## Módulos

En el orden en que conviene recorrerlos: del frontend hacia adentro.

| Módulo | Puerto | Qué es |
|---|---|---|
| `frontend-web` | 5173 | SPA React + Vite con MSAL. No es módulo Maven |
| `bff-web` | 8080 | Backend for Frontend: valida el token y agrega respuestas de varios servicios |
| `ms-usuarios` | 8081 | Identidad y perfil. Dueño del schema `usuarios` |
| `ms-proyectos` | 8082 | Vitrina de proyectos y comentarios. Schema `proyectos` |
| `ms-contacto` | 8083 | Solicitudes de colaboración. Schema `contacto` |
| `common-seguridad` | — | Librería compartida: validación de JWT (audiencia, roles, dominios), manejo de errores y registro de peticiones |
| `docker` | — | Dockerfile de los cuatro servicios e inicialización de PostgreSQL |
| `infra` | — | Despliegue en AWS: `aws.sh` y la definición de la tarea de ECS |

## Arquitectura

```
Navegador  →  API Gateway  →  ALB  →  BFF  →  microservicios  →  PostgreSQL
              (valida JWT)           (valida JWT)  (valida JWT)
```

El token se valida **tres veces**: en el API Gateway, en el BFF y en el microservicio. Es defensa
en profundidad — quien llegue salteándose una capa igual necesita un token válido.

El frontend solo conoce al BFF. Publicar, editar y borrar un proyecto van directo a `ms-proyectos`:
ahí no hay nada que componer.

## Requisitos

- **Java 21**
- **Maven 3.9+**
- **Docker** con Compose v2 (para PostgreSQL)
- Un tenant de **Azure AD** con dos registros de aplicación. La guía de configuración del IDaaS se
  mantiene fuera del repositorio: pídela al equipo

## Levantar el proyecto

```bash
cp .env.example .env          # completa AZURE_TENANT_ID y AZURE_CLIENT_ID
docker compose up -d          # PostgreSQL

export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
mvn clean install

set -a && source .env && set +a
mvn -pl ms-usuarios  spring-boot:run    # 8081
mvn -pl ms-proyectos spring-boot:run    # 8082
mvn -pl ms-contacto  spring-boot:run    # 8083
mvn -pl bff-web      spring-boot:run    # 8080

cd frontend-web && npm install && npm run dev    # 5173
```

Con `-Dspring-boot.run.profiles=dev` se cargan datos de ejemplo, útiles para la demo.

### Variables

`AZURE_TENANT_ID` y `AZURE_CLIENT_ID` no tienen valor por defecto a propósito: sin ellas los
servicios fallan al arrancar, en vez de levantar con la seguridad incompleta. **`.env` nunca se
sube al repositorio.**

| Variable | Obligatoria | Qué es |
|---|---|---|
| `AZURE_TENANT_ID` | **Sí** | Directory (tenant) ID. Arma el `issuer-uri` |
| `AZURE_CLIENT_ID` | **Sí** | Client-id del registro de la **API**. Es la audiencia del token |
| `CORS_ORIGENES` | No | Por defecto `http://localhost:5173`. En la nube se le suma el dominio del API Gateway |
| `LOG_NIVEL` | No | Por defecto `INFO`. `DEBUG` registra también las peticiones que salen bien |
| `DB_URL` · `DB_USER` · `DB_PASSWORD` | No | Valores locales por defecto |
| `MS_USUARIOS_URL` · `MS_PROYECTOS_URL` · `MS_CONTACTO_URL` | No | Por defecto `localhost` en sus puertos |
| `S3_BUCKET_ADJUNTOS` | No | Bucket de los archivos adjuntos. Vacío en local: sin él, firmar una subida falla en vez de escribir en un bucket equivocado |
| `AWS_REGION` | No | Por defecto `us-east-1` |

## Endpoints

### `ms-usuarios` — `/api/v1/usuarios`

| Método y ruta | Qué hace |
|---|---|
| `GET /me` | Auto-provisiona o lee el perfil propio desde los claims del token |
| `PUT /me` | Completa carrera, sede, bio, teléfono y redes. El nombre viene del token |
| `PATCH /me/visibilidad` | Muestra u oculta el perfil en las búsquedas |
| `GET /me/redes` | Redes sociales del usuario autenticado |
| `GET /{id}` | Perfil público de otra persona (**sin** teléfono ni redes) |
| `GET /?carrera=&sede=` | Listado de perfiles visibles, con filtros |

### `ms-proyectos` — `/api/v1/proyectos`

| Método y ruta | Qué hace |
|---|---|
| `POST /` · `GET /` · `GET /{id}` | Publica, lista y muestra proyectos |
| `PUT /{id}` · `DELETE /{id}` | Edita y borra; solo el propietario |
| `GET`/`POST /{id}/comentarios` | Hilo de comentarios. Comentar no comparte datos de contacto |
| `POST /{id}/colaboradores` | Suma un colaborador; solo el propietario y solo en proyectos compartidos |
| `POST /adjuntos/firma` | Autoriza subir un archivo: devuelve una URL temporal para mandarlo directo a S3 |

### `ms-contacto` — `/api/v1/colaboraciones`

| Método y ruta | Qué hace |
|---|---|
| `POST /` | Pide contacto a otra persona |
| `PATCH /{id}/responder` | Acepta o rechaza; solo el destinatario |
| `GET /recibidas` · `GET /enviadas` | Las dos bandejas |

### `bff-web` — `/api/v1/bff`

| Método y ruta | Por qué existe |
|---|---|
| `GET /mi-perfil` | Junta perfil y redes en una sola llamada |
| `GET /vitrina` | Suma a cada proyecto quién lo publicó, que `ms-proyectos` no sabe |
| `GET`/`POST /vitrina/{id}/comentarios` | Suma el nombre de quien comentó |
| `/colaboraciones/**` | Arma los datos de contacto desde el perfil de cada parte, resuelve **cuál de los dos lados** le toca ver a quien consulta, y suma el nombre del proyecto |

## Seguridad

Todos los servicios son OAuth2 Resource Server y validan firma, vigencia, emisor y **audiencia**.
Solo `/actuator/health`, `/swagger-ui/**` y `/v3/api-docs/**` quedan abiertos.

Los errores salen en **Problem Details (RFC 9457)**, con el motivo en `detail`:

| Código | Cuándo |
|---|---|
| **400** | La petición no tiene sentido |
| **401** | Falta el token, venció o no es válido |
| **403** | Token válido sin permiso: rol, dominio, o la acción es de otra persona |
| **404** | No existe, o su dueño lo ocultó |
| **409** | Choca con el estado actual (ya enviada, ya respondida) |

### Swagger UI

`http://localhost:{8080..8083}/swagger-ui.html` — botón **Authorize** y pegar el access token, sin
la palabra `Bearer`.

## Tests

```bash
mvn test        # 44 pruebas
```

Cubren que el contexto levante, que una ruta protegida responda 401 sin token, que un token sin rol
reciba 403 y no un 500, que un dominio externo reciba 403, y el recorrido completo del
consentimiento en `ms-contacto`: pedir contacto ofreciendo el propio, aceptar, y recién entonces
ver **las dos partes** los datos de la otra.

## Nota sobre la versión de Spring Boot

Se usa **Spring Boot 4.1.1** con Spring Security forzado a **7.1.1**. El enunciado pedía "3.x" y a
la vez "sin CVEs abiertos", y en agosto de 2026 esas dos condiciones son incompatibles: la rama 3.x
está EOL en open source y Spring Security 6.5.x arrastra CVE sin fix público. El razonamiento
completo está en [`CLAUDE.md`](CLAUDE.md) §3.1.
