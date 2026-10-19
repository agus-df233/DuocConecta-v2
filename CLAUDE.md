# CLAUDE.md — Contexto y reglas del proyecto DuocConecta

Este archivo es el contexto permanente para cualquier sesión del agente sobre este repositorio.
Léelo antes de escribir código. Si una decisión cambia, **actualiza este archivo**.

---

## 1. Qué es DuocConecta

Plataforma de **networking y vitrina de proyectos** para la comunidad de Duoc UC.
Asignatura **DSY1107 · Desarrollo Cloud Native I**.

El problema que resuelve: hoy el conocimiento que genera la comunidad (proyectos, prompts, recursos)
vive disperso en grupos de WhatsApp y Drives personales. Cada semestre se pierde, no cruza entre
carreras ni entre sedes, y quien llega nuevo empieza de cero. DuocConecta centraliza ese intercambio
en una plataforma institucional, con un mecanismo de contacto que **respeta el consentimiento**:
nadie ve los datos de contacto de otro hasta que ambos aceptan colaborar.

### Objetivo de la EP1

La EP1 evalúa tres cosas: **autenticación**, **validación de JWT** y **despliegue en la nube**.
Todo eso sigue vigente en la v2 y **no se toca**: la mensajería se suma encima.

### Objetivo de la EP2 (este repositorio, v2)

**Mensajería asíncrona** sin afectar la lógica existente: colas con DLQ, productores y
consumidores con confirmación explícita, y un **microservicio administrador** con API REST. El
plataforma usa **dos brokers a la vez**: **Amazon SQS** con **SNS** como exchange topic, y
**RabbitMQ** (clúster de 2 nodos). Ver §12.

Este repositorio nació como **copia del código de la EP1** (rama `developer`, `1b205d2`), sin su
historial ni sus remotes. El repositorio de la EP1 no se modifica.

---

## 2. Arquitectura

**Lo que pide la referencia del enunciado:**

```
React (SPA)  →  BFF  →  API Manager  →  microservicios  →  PostgreSQL
```

**Lo que está construido y desplegado:**

```
Navegador  →  API Gateway  →  ALB  →  BFF  →  microservicios  →  PostgreSQL
              (valida JWT)           (valida JWT)  (valida JWT)
```

**La diferencia, y cómo se justifica.** El API Manager quedó **delante** del BFF y no detrás. Es
la única puerta pública: valida el token antes de que la petición entre a la red privada, y sirve
además el frontend desde S3 bajo el mismo dominio (Azure AD exige HTTPS en los URI de redirección
y CloudFront está bloqueado en el laboratorio). El BFF llama a los microservicios por `localhost`,
porque los cuatro comparten una tarea de Fargate: Cloud Map no está disponible en el lab.

**Lo que no cambia es lo que la EP1 evalúa:** el token se valida **tres veces** —API Gateway, BFF
y microservicio— y el frontend sigue hablando solo con el BFF.

**IDaaS, dos proveedores** (EP2), ambos con **OIDC Authorization Code + PKCE**:
- **Microsoft Entra ID** para la cuenta Duoc ("Entrar con Microsoft"): MSAL, access token de la API.
- **Amazon Cognito** solo para **Google** ("Entrar con Google"): oidc-client-ts, ID token
  (Cognito no pone `aud` en el access token; el ID token sí, y trae `email`).

La aplicación nunca almacena contraseñas. El backend acepta los dos emisores con
`DecodificadorMultiEmisor` (common-seguridad): lee `iss` sin confiar en él solo para elegir y
valida firma, vigencia, emisor y audiencia **con las claves y la audiencia de ese emisor**. El API
Gateway lo hace con un **autorizador Lambda** (`infra/autorizador/index.mjs`, sin dependencias),
porque un autorizador JWT nativo acepta un solo emisor. Con la Lambda, sin token → 401 y token
inválido → 403 en el gateway; el backend responde 401 igual.

- El **frontend** nunca llama a los microservicios directamente: solo conoce al BFF.
- El **API Gateway** valida el token y enruta; es lo primero que ve una petición.
- El **BFF** valida el token otra vez y agrega respuestas de varios microservicios en una llamada.
- Los **microservicios** ejecutan la lógica de negocio y validan el token una tercera vez.

Publicar, editar y borrar un proyecto van directo a `ms-proyectos` a través del API Gateway: ahí
no hay nada que componer y pasar por el BFF sería una capa de más.

---

## 3. Decisiones fijas (respétalas)

### Stack

| Qué | Decisión |
|---|---|
| Lenguaje | **Java 21** |
| Framework | **Spring Boot 4.1.1** (ver §3.1) |
| Spring Security | **7.1.1** (forzado por `<spring-security.version>`) |
| Build | **Maven**, monorepo multi-módulo |
| Base de datos | **PostgreSQL 16.15**, 1 contenedor, **un schema por servicio** |
| Migraciones | **Flyway** |
| Documentación API | **springdoc-openapi 3.1.0** (Swagger UI) |
| Adjuntos | **Amazon S3** (en la base solo la referencia; no aplica a `ms-usuarios` en v1) |
| Prefijo de API | **`/api/v1`** |

### 3.1 Por qué Spring Boot 4.1.1 y no 3.x

La decisión original del enunciado decía "Spring Boot 3.x" y a la vez "última patch de una línea
mantenida y sin CVEs abiertos". **En agosto de 2026 esas dos condiciones son incompatibles**:

- Toda la rama 3.x está EOL en open source. La línea 3.5 terminó su soporte OSS el **30-jun-2026**
  (última patch: 3.5.16); 3.4 terminó en dic-2025.
- Spring Security 6.5.x arrastra CVE-2026-47841 y CVE-2026-47842 cuyos fixes OSS **no existen**
  (6.5.12 es solo Enterprise Support).

Se priorizó "mantenida y sin CVEs". Se usa **Boot 4.1.1** (soporte OSS hasta jul-2027) con
`spring-security.version` forzado a **7.1.1**, que corrige los tres CVE publicados el 20-ago-2026:

| CVE | Qué es | Fix OSS |
|---|---|---|
| CVE-2026-41707 | Replay de proof DPoP por desalojo de caché | 7.1.1 |
| CVE-2026-47841 | Bypass de user verification en WebAuthn | 7.1.1 |
| CVE-2026-47842 | `AesBytesEncryptor` determinista con IV nulo | 7.1.1 |

### 3.2 Consecuencias de Spring Boot 4 (¡importante al escribir código!)

Boot 4 renombró artefactos y subió a Jackson 3. No copies recetas de Boot 3:

| Boot 3 (NO usar) | Boot 4 (usar) |
|---|---|
| `spring-boot-starter-web` | **`spring-boot-starter-webmvc`** |
| `spring-boot-starter-oauth2-resource-server` | **`spring-boot-starter-security-oauth2-resource-server`** |
| Flyway transitivo | **`spring-boot-starter-flyway` explícito** |
| `com.fasterxml.jackson.*` | Jackson 3: **`tools.jackson.*`** |
| `@MockBean` / `@SpyBean` | **`@MockitoBean` / `@MockitoSpyBean`** |
| `AntPathRequestMatcher`, `.and()` | **solo lambda DSL**; `PathPatternRequestMatcher` con patrones absolutos |

Los DTOs son **records**, así no hace falta tocar Jackson directamente.

### Roles y dominios de correo

El rol se determina por el **dominio del correo institucional** que viene en el token:

| Dominio | Rol |
|---|---|
| `@duocuc.cl` | `ESTUDIANTE` |
| `@profesor.duoc.cl` | `PROFESOR` |
| `@duoc.cl` | `ACADEMICO` |
| cualquier otro | **rechazar** → 403, no se auto-provisiona el perfil |

**Rol `ADMINISTRADOR` (EP2).** No sale de un dominio: **se suma** al rol del dominio, nunca lo
reemplaza (si lo reemplazara, el administrador recibiría 403 en toda la plataforma). Llega por
dos vías, y la elección se hace al configurar el tenant:

- **App Role** `ADMINISTRADOR` en Entra ID, en el claim `roles` (vía preferida).
- **Lista** `duocconecta.seguridad.administradores: ${ADMINISTRADORES:}`: correos separados por
  coma. El valor real va en `.env` o en la task definition, **nunca en el repositorio** (es público).

Un dominio no autorizado queda sin permisos aunque traiga el App Role o esté en la lista. La regla
vive en `ConversorRolesJwt`; las expresiones `@PreAuthorize` compartidas, en `ReglasDeAcceso`
(`ROLES_DE_LA_PLATAFORMA`, `SOLO_ADMINISTRADOR`).

**Cuentas de Google (EP2).** Entran por **Amazon Cognito**, que federa a Google (user pool
`duocconecta-usuarios`, `aws.sh cognito`; guía en `docs/guia-cognito-google.md`). Entra ID no se
toca. Su dominio (`gmail.com`) **no** se agrega al mapa: cualquiera con Gmail que lograra entrar
tendría acceso. En cambio, cada cuenta se anota con su rol en
`duocconecta.seguridad.correos-permitidos: ${CORREOS_PERMITIDOS:}` (`correo=ROL`, separados por
coma). La comparación es exacta, el correo de la lista tiene prioridad sobre su dominio, una
entrada mal escrita detiene el arranque y `ADMINISTRADOR` no se acepta ahí (para eso está la
lista de administradores). Fuera de la lista, un Gmail sigue recibiendo 403.

- Emisor adicional en `duocconecta.seguridad.emisores-adicionales` (`COGNITO_ISSUER_URI`,
  `COGNITO_AUDIENCIA`). Vacíos = solo Microsoft, como en local sin Cognito.
- Para Cognito se exige **`email_verified = true`** (por emisor: Entra ID no emite ese claim).
- Roles del token: `claims-roles: [roles, cognito:groups]` (App Roles de Entra + grupos de Cognito).

La lógica vive **centralizada y configurable** en `ResolvedorRol` + `PropiedadesSeguridad`
(`duocconecta.seguridad.dominios` y `correos-permitidos` en `application.yml`), porque pueden
sumarse dominios y cuentas.

**Regla crítica:** el match del dominio es **exacto sobre el texto después del último `@`**, nunca
`endsWith`. Con `endsWith`, `alguien@profesor.duoc.cl` haría match también con `duoc.cl` y quedaría
clasificado mal según el orden de iteración del mapa.

### Fuera de alcance

- **Construido en la EP2:** notificaciones (SQS/SNS y RabbitMQ), moderación de la vitrina, rol
  `ADMINISTRADOR`, vista de administración con dashboard en el frontend, cuentas de Google por lista.
- **No generar todavía (EP3 o v3):** `ms-prompts`, `ms-publicaciones`, Kafka, correo real con SES,
  patrón outbox, moderación de colaboraciones.
- **RabbitMQ:** el diseño nació en el repositorio de Javiera y se portó aquí como **segundo
  broker** detrás de `PublicadorEventos`, sin tocar el negocio (§12.1). Su repositorio sigue
  siendo de ella; aquí no se copió su cambio a Java 25.

---

## 4. Alcance por fases

| Fase | Qué | Estado |
|---|---|---|
| **1** | Monorepo, `common-seguridad`, `ms-usuarios`, `bff-web`, PostgreSQL, Swagger, tests | Construida |
| **2** | `frontend-web` React + Vite con MSAL (Authorization Code + PKCE) | Construida |
| **3** | `ms-proyectos` (CRUD) + agregación en el BFF → llena la vitrina | Construida |
| **4** | `ms-contacto` (solicitudes de colaboración, HU-17 a HU-20) + agregación en el BFF | Construida |
| **5 · EP2** | `common-eventos`, productores en ms-proyectos y ms-contacto, `ms-notificaciones` (consumidores SQS) | Construida |
| **6 · EP2** | `ms-mensajeria` (administrador), rol `ADMINISTRADOR` y moderación de la vitrina | Construida |

El objetivo de la demo es: **login institucional → IDaaS → vitrina de proyectos, con el perfil del
usuario relleno y las solicitudes de contacto funcionando de punta a punta.**

El front **no es módulo Maven**: se construye con npm/Vite aparte. En la nube se publica en S3 y se
sirve por el API Gateway; en local corre en `http://localhost:5173`, origen que el CORS del BFF
acepta por defecto.

---

## 5. Convenciones de código (obligatorias)

### Idioma

**Todo en español de Chile**, con trato de **tú**: comentarios, mensajes de error, commits,
documentación, textos de la interfaz y descripciones de OpenAPI. Nada de voseo ("tienes", no
"tenés").

**Los nombres de clases, métodos y variables también van en español.** La única excepción son los
nombres propios de una tecnología o estándar, que se conservan tal cual: `Jwt`, `OpenApi`, `Cors`,
`Http`, `Bff`, `Flyway`, y el sufijo `Exception` que exige Java.

| En vez de | Se usa |
|---|---|
| `UsuarioController` | `ControladorUsuarios` |
| `UsuarioService` · `UsuarioRepository` | `ServicioUsuarios` · `RepositorioUsuarios` |
| `UsuariosClient` | `ClienteUsuarios` |
| `...Request` · `...RequestDTO` | `...Datos` |
| `...Response` · `...ResponseDTO` · `...Dto` | `...Respuesta` |
| `GlobalExceptionHandler` | `ManejadorErrores` |
| `SeguridadConfig` · `OpenApiConfig` | `ConfiguracionSeguridad` · `ConfiguracionOpenApi` |

Las clases `*Application` conservan su nombre: coinciden con el nombre del artefacto Maven, que a
su vez es el del repositorio de ECR y el del contenedor en la task definition.

Los comentarios explican **el qué y el por qué**, en lenguaje simple y **breve**: una o dos líneas.
Si un comentario necesita un párrafo, casi siempre lo que hace falta es un nombre mejor.
Debe haber un comentario breve arriba de:
- cada clase,
- cada método público,
- cada bloque de lógica no obvia.

### Orden de capas

Cada microservicio organiza su paquete en estas 6 capas, **en este orden**:

1. **`domain/`** — entidades JPA y enums del dominio
2. **`repository/`** — interfaces Spring Data JPA
3. **`service/`** — lógica de negocio
4. **`controller/`** — controladores REST (`@RestController`)
5. **`dto/`** — objetos de entrada/salida de la API
6. **`config/`** — seguridad, CORS, OpenAPI y demás configuración

### Reglas duras

- **Nunca exponer entidades JPA** en la API. Siempre DTOs (records).
- **`telefono` y `redes` nunca salen en respuestas públicas.** Son datos de contacto privados.
  `GET /usuarios/{id}` y el listado devuelven `PerfilPublicoRespuesta`, que no los tiene.
  `/me/redes` devuelve solo las del propio usuario autenticado. Compartirlas con terceros queda
  sujeto al consentimiento mutuo en EP2.
- El `oid` y el correo se extraen **siempre del `Jwt`**, nunca por parámetro de la petición.
  Usá el componente `UsuarioActual`.
- Validación de entrada con **`jakarta.validation`**.
- Cada endpoint documentado con **Javadoc en español** *y* **`@Operation(summary = "...")`**.

### Seguridad

- Cada microservicio y el BFF son **OAuth2 Resource Server** y validan el JWT: firma, vigencia,
  issuer y **audience** (esta última con un `OAuth2TokenValidator` propio, `ValidadorAudiencia`).
- Autorización por rol con `@PreAuthorize`.
- Códigos de respuesta, y la diferencia importa:

  | Código | Cuándo |
  |---|---|
  | **200 / 201** | Todo bien |
  | **400** | La petición no tiene sentido (por ejemplo, pedirte contacto a ti mismo) |
  | **401** | Falta el token, venció o no es válido — *no sé quién eres* |
  | **403** | Token válido pero sin permiso: rol insuficiente, dominio no autorizado, o la acción es de otra persona — *sé quién eres y no puedes* |
  | **404** | El recurso no existe, o su dueño lo ocultó |
  | **409** | Choca con el estado actual: la solicitud ya está enviada, o ya fue respondida — *puedes, pero ahora no* |

  Un conflicto de estado **nunca** se responde con 403: decirle "no tienes permiso" a quien sí lo
  tiene despista a quien usa la plataforma y a quien la depura.
- Todos los servicios responden errores en **Problem Details (RFC 9457)**, con el motivo en el
  campo `detail`. Los manejadores comunes viven en `ManejadorErroresBase` (`common-seguridad`).
- **CORS explícito**: orígenes y métodos declarados, **sin comodines**.

---

## 6. Restricciones

- **Nunca subir secretos.** `issuer-uri`, `client-id`, `tenant-id`, credenciales de base de datos:
  todo por variable de entorno / `application.yml` con `${VAR:default-dev}`. Nunca hardcodear.
  `.env` está en `.gitignore`; usa `.env.example` como plantilla.
- Los eventos **nunca** llevan correo, teléfono ni redes: solo identificadores (§12).
- Ningún correo de administrador, ARN ni número de cuenta en el repositorio: es público.
- No exponer entidades JPA directamente.
- `telefono` / `redes` nunca en respuestas públicas.

---

## 7. Estructura de módulos

```
DuocConecta/
├── pom.xml                 # parent (packaging pom), versiones centralizadas
├── docker-compose.yml      # 1 contenedor postgres:16.15-alpine
├── docker/postgres/init.sql
├── docs/azure-entra-id.md  # guía del IDaaS de Microsoft (fuera del repositorio, a propósito)
│                           # docs/guia-cognito-google.md: login con Google (también local)
│
│   # Los módulos, del frontend hacia adentro. En GitLab se ven en orden
│   # alfabético: Git ordena así las entradas del árbol y no se puede cambiar.
├── frontend-web/           # React + Vite (no es módulo Maven)
├── bff-web/                # puerto 8080
├── ms-usuarios/            # puerto 8081, schema `usuarios`
├── ms-proyectos/           # puerto 8082, schema `proyectos`  — vitrina de proyectos
├── ms-contacto/            # puerto 8083, schema `contacto`   — solicitudes de colaboración
├── ms-notificaciones/      # puerto 8084, schema `notificaciones` — consumidores SQS y avisos (EP2)
├── ms-mensajeria/          # puerto 8085, sin base — administrador de colas, tópicos y suscripciones (EP2)
├── common-seguridad/       # validación de JWT, manejo de errores y registro, compartidos
├── common-eventos/         # contrato de eventos, publicación en SNS y topología (EP2)
├── docker/                 # Dockerfile de los servicios e init.sql de PostgreSQL
└── infra/                  # aws.sh y la definición de la tarea de ECS
```

**Frontend.** Dos pantallas dentro de la aplicación (`Vitrina` y `Perfil`), más `Administración`
solo para `ADMINISTRADOR`. Ya no hay pantalla de diagnóstico del token: para mostrar el JWT en la
demo se usa DevTools (pestaña Red, cabecera `Authorization`) y jwt.ms. Las solicitudes de colaboración están **dentro de Perfil**
y no en una vista aparte: son datos personales, no de la vitrina. El diseño sigue al prototipo
aprobado (**DuocConecta v2**) y vive en clases de `estilos.css`, nunca en estilos en línea; los
colores son variables en `:root`. `catalogo.js` concentra carreras, sedes, estados, iniciales y los
tipos de aviso: es el archivo que se cambia cuando llegue el listado oficial de carreras.

Piezas de la interfaz que conviene conocer antes de tocarla:

| Pieza | Qué hace |
|---|---|
| `CentroNotificaciones.jsx` | Consulta los avisos cada 20 s; lo nuevo sale como push. Las solicitudes pendientes se responden ahí |
| `Ventana.jsx` | Toda ventana modal pasa por aquí: Esc cierra, el foco queda atrapado y vuelve al cerrar |
| `Accesibilidad.jsx` + `preferencias.js` | Tamaño del texto, alto contraste, reducir movimiento y duración de los avisos, guardados en el navegador |
| `eventos.js` | Avisa entre pantallas que no se conocen (responder en la cabecera recarga el perfil) |
| `enlaces.js` | Enlace directo a un proyecto: `/#proyecto-<id>`, que sobrevive al viaje del login |
| `FiltrosVitrina.jsx` | Los filtros de la EP1, a propósito: buscador y desplegables (Escuela/Carrera en uno, Herramienta, Estado) en una sola fila. No se guardan entre visitas |
| `Aviso.jsx` | Mensaje flotante; con una acción ("Deshacer") dura más |
| `Alt+N` | Abre y cierra el centro de notificaciones (`aria-keyshortcuts`). Los push duran 6 s, 12 s o hasta cerrarlos, y el mouse o el foco los detiene |

**La biblioteca de prompts no se construye aquí:** es de la v3. En la cabecera queda solo el ítem
apagado "Prompts · Pronto".

`ms-proyectos` y `ms-contacto` nacieron como repositorios aparte (`ms-repositorios` y
`ms-contacto`) y se integraron al monorepo: se les cambió el paquete a `cl.duoc.duocconecta.*`, se
les borró la seguridad propia para que usen `common-seguridad`, se pasaron sus rutas a `/api/v1` y
sus tablas a Flyway. `ms-publicaciones` (biblioteca de recursos y prompts) queda para la EP2.

**Sobre `common-seguridad`:** el validador de audiencia, el conversor de roles y el mapa dominio→rol
son idénticos en `ms-usuarios` y `bff-web`, y los microservicios que vienen (`ms-proyectos`,
`ms-contacto`) los van a necesitar igual. Duplicarlos garantizaría que se desincronicen.

---

## 8. Cómo levantar el proyecto

```bash
# Java 21 (el proyecto compila con release 21)
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 1. Base de datos
docker compose up -d

# 2. Compilar todo
mvn clean install

# 3. Levantar los servicios (dos terminales)
mvn -pl ms-usuarios spring-boot:run     # http://localhost:8081
mvn -pl bff-web     spring-boot:run     # http://localhost:8080
```

Swagger UI: `http://localhost:8081/swagger-ui.html` y `http://localhost:8080/swagger-ui.html`.
Health: `http://localhost:8081/actuator/health` y `http://localhost:8080/actuator/health`.

Ver `README.md` para las variables de entorno y `docs/azure-entra-id.md` para configurar el IDaaS.

---

## 9. Endpoints de `ms-usuarios`

| Método y ruta | Qué hace | Auth |
|---|---|---|
| `GET /api/v1/usuarios/me` | Auto-provisiona/lee el perfil del usuario autenticado desde los claims | rol válido |
| `PUT /api/v1/usuarios/me` | Actualiza el perfil propio | rol válido |
| `PATCH /api/v1/usuarios/me/visibilidad` | Alterna `visible` | rol válido |
| `GET /api/v1/usuarios/me/redes` | Devuelve las redes sociales del usuario autenticado | rol válido |
| `GET /api/v1/usuarios/{id}` | Perfil público (sin telefono/redes; respeta `visible`) | autenticado |
| `GET /api/v1/usuarios?carrera=&sede=` | Listado público de perfiles visibles, con filtros | autenticado |

## 9.1 Endpoints de `ms-proyectos` y `ms-contacto`

| Método y ruta | Qué hace |
|---|---|
| `POST /api/v1/proyectos` | Publica un proyecto en la vitrina |
| `GET /api/v1/proyectos` | Lista los proyectos visibles |
| `GET /api/v1/proyectos/{id}` | Detalle de un proyecto |
| `PUT /api/v1/proyectos/{id}` | Edita o cambia la visibilidad; solo el propietario (HU-11) |
| `DELETE /api/v1/proyectos/{id}` | Lo borra; solo el propietario |
| `POST /api/v1/proyectos/admin/abiertos` · `PUT …/abiertos/{id}` | Solo ADMINISTRADOR: publica o edita un **proyecto abierto** (ver "Modelo de proyecto") |
| `GET /api/v1/proyectos/{id}/comentarios` | Hilo de comentarios |
| `POST /api/v1/proyectos/{id}/comentarios` | Comenta; comentar no comparte datos de contacto |
| `POST /api/v1/colaboraciones` | Pide contacto a otra persona |
| `PATCH /api/v1/colaboraciones/{id}/responder` | Acepta o rechaza; solo el destinatario |
| `PATCH /api/v1/colaboraciones/{id}/retirar` | La retira quien la pidió, solo si sigue pendiente (409 si no). `RETIRADA` libera el cupo del proyecto |
| `GET /api/v1/colaboraciones/recibidas` | Bandeja de entrada |
| `GET /api/v1/colaboraciones/enviadas` | Bandeja de salida, con los datos ya compartidos |

### Lo que el frontend le pide al BFF y no a los microservicios

| Ruta del BFF | Por qué existe |
|---|---|
| `GET /api/v1/bff/mi-perfil` | Junta el perfil y las redes en una sola llamada |
| `GET /api/v1/bff/vitrina` | Suma a cada proyecto **quién lo publicó** (nombre, carrera, sede), que ms-proyectos no sabe. Es lo que permite filtrar la vitrina por carrera. En los **abiertos** el autor es `DuocConecta` con la carrera elegida, sin consultar el perfil del admin |
| `GET`/`POST /api/v1/bff/vitrina/{id}/comentarios` | Suma el nombre de quien escribió cada comentario |
| `/api/v1/bff/colaboraciones/**` | Al aceptar, el BFF lee el perfil de quien acepta y arma con él los datos de contacto, en vez de pedirle que los escriba a mano |
| `GET /api/v1/bff/admin/resumen` | Dashboard de administración: junta las estadísticas de ms-usuarios y ms-proyectos y cuenta los proyectos **por carrera** con `ResolvedorAutores`. No reenvía los `oid` de las dueñas |

`mi-perfil` devuelve también `roles`, los roles efectivos que calculó el BFF. El frontend los usa
para mostrar la vista de administración: el App Role viaja en el token, pero la lista
`ADMINISTRADORES` solo existe en el backend.

Publicar, editar y borrar un proyecto sí van directo a `ms-proyectos`: ahí no hay nada que
componer y pasar por el BFF sería una capa de más. La traducción de identificador a persona la
hace `ResolvedorAutores`, que consulta `GET /api/v1/usuarios/por-oid/{oid}` una vez por persona y
no una por fila, y **no cachea entre peticiones**: un dato guardado de más sobreviviría a que
alguien se ocultara.

### Modelo de proyecto

`resumen` es la línea que se lee en la tarjeta; `descripcion` es el detalle completo.

**Proyectos abiertos** (`abierto`, `carrera`, migración `V5`): los publica el ADMINISTRADOR para que
la vitrina tenga más proyectos a la vista. Siempre públicos y sin colaboradores; la tarjeta dice
"Publicado por DuocConecta" con la carrera y la sede que eligió el admin (así los filtros por carrera
siguen sirviendo). **Solo se comentan**: el BFF responde 400 si se pide colaborar en uno, el `PUT`
normal y agregar colaboradores responden 409, y comentar publica `proyecto.comentado` **sin
destinatario** (no hay aviso; la auditoría lo registra igual). No aparecen en "Mis proyectos" del
admin, se editan desde Administración, y las estadísticas los cuentan aparte (`porCarreraAbiertos`).

`herramientas` es texto libre y se llama así —no "stack"— a propósito: DuocConecta es de toda la
comunidad, y un proyecto de Diseño lista Figma y uno de Administración lista Excel igual que uno
de Informática lista React. Una lista cerrada dejaría carreras fuera.

"Público" significa **sin datos de contacto**, no sin autenticación: la propuesta de arquitectura
pide validación de JWT en cada capa. Solo `/actuator/health`, `/swagger-ui/**` y `/v3/api-docs/**`
quedan abiertos.

---

## 10. Cómo sumar un microservicio nuevo

Receta para que un módulo nuevo encaje sin retrabajo. `ms-proyectos` es el primer caso.

| Qué | Valor |
|---|---|
| Puerto | `ms-usuarios` 8081 · `ms-proyectos` 8082 · `ms-contacto` 8083 · `ms-notificaciones` 8084 · `ms-mensajeria` 8085; el siguiente, 8086 |
| Migraciones | `V1..V899` son cambios de esquema; **`V900+` son datos de ejemplo** y solo se cargan con el perfil `dev`. Por eso `ms-proyectos` lleva `out-of-order: true` en ese perfil: una migración real nueva siempre queda "atrasada" respecto de los datos de ejemplo ya aplicados. En la nube el perfil `dev` no se activa nunca |
| Schema propio | **`proyectos`** (ya creado en `docker/postgres/init.sql`) |
| Paquete raíz | `cl.duoc.duocconecta.proyectos` |
| Prefijo de rutas | `/api/v1/proyectos` |

**Pasos:**

1. Declarar el módulo en `<modules>` del `pom.xml` raíz.
2. Copiar las dependencias de `ms-usuarios/pom.xml`, **incluida `common-seguridad`**.
3. Copiar `config/ConfiguracionSeguridad.java` y `config/ConfiguracionOpenApi.java` tal cual: solo cambia el
   paquete. Eso ya deja el servicio validando el JWT igual que los demás.
4. Respetar el orden de capas: `domain` → `repository` → `service` → `controller` → `dto` → `config`.
5. `application.yml`: puerto propio, `spring.flyway.schemas` y `default-schema` con el schema
   propio, y **`create-schemas: true`** (en RDS no se ejecuta `init.sql`).
6. Migración `V1__proyectos.sql` en `src/main/resources/db/migration/`.
7. Tests: copiar el patrón de `MsUsuariosApplicationTests` — contexto que levanta y ruta protegida
   que responde 401. En `application-test.yml` usar `jwk-set-uri` y **nunca `issuer-uri`**
   (este último hace descubrimiento OIDC contra la red y el test falla sin internet), y tapar el
   `issuer-uri` heredado con `issuer-uri: ""`.

**Nunca hace falta tocar** `common-seguridad`: la validación de audiencia, el mapeo de roles y
`UsuarioActual` ya sirven a cualquier microservicio.

El despliegue ya está preparado: el repositorio de ECR, el contenedor en
`infra/task-definition.json` y el target group del ALB existen de antemano. Cuando el módulo esté
en el repo, se despliega con `make desplegar SERVICIO=ms-proyectos`.

---

## 11. Infraestructura y despliegue

```
Navegador          API Gateway HTTP API      ALB          ECS Fargate — 1 tarea, 6 contenedores
   (front)     →   (validación de JWT)   →  (rutas)  →    ├── bff-web           :8080
                                                          ├── ms-usuarios       :8081  → RDS PostgreSQL
                                                          ├── ms-proyectos      :8082  ─┐ publican en
                                                          ├── ms-contacto       :8083  ─┘ SNS (EP2)
                                                          ├── ms-notificaciones :8084  ← consume SQS y RabbitMQ
                                                          └── ms-mensajeria     :8085  → administra los dos brokers

EC2 duocconecta-rabbitmq (t3.small) — docker-compose: rabbit1 :5672 + rabbit2 :5673, clúster de 2 nodos
```

ms-mensajeria va en un **servicio de ECS aparte** (`SERVICIOS_APARTE`): un servicio admite como
máximo 5 target groups. RabbitMQ va en una **EC2** porque Amazon MQ no está habilitado en el lab;
`make apagar` la detiene y `make iniciar` la enciende. Sin la EC2, `desplegar` deja
`RABBITMQ_HABILITADA=false` y la plataforma sigue solo con SNS/SQS.

Con varias JVM en la misma tarea (seis desde la EP2) hay tres cosas que **no se ajustan solas** y que costaron un
despliegue entero de depuración:

| Qué | Valor | Por qué |
|---|---|---|
| Memoria | 2 vCPU / **6 GB** de tarea (EP2; eran 4 GB con cuatro servicios), tope de 1 GB por contenedor y 768 MB para ms-mensajeria | El heap lo acota la imagen con `MaxRAMPercentage`. Sin topes, la primera JVM en arrancar se queda con casi toda la RAM y las demás mueren |
| Período de gracia del health check | **600 s** (EP2; eran 420 s y antes 240 s) | ECS mata la tarea entera si **cualquiera** de los target groups la ve enferma al vencer la gracia. Con tres servicios corriendo Flyway al arrancar, la última en levantar se llevaba puestas a las otras tres |
| Timeout del health check | **15 s**, no los 5 s por defecto | Con el recolector serie y la CPU compartida, una pausa de GC hace que `/actuator/health` tarde más de 5 s y el ALB da el destino por muerto aunque la aplicación esté viva |

**La trampa más cara:** el security group de las tareas abría los puertos con una lista escrita a
mano (`for p in 8080 8081 8082`). Al sumar `ms-contacto` en el 8083, el ALB no podía alcanzarlo:
los paquetes se descartaban en el security group, así que el contenedor arrancaba perfecto, los
registros no mostraban ni un error, y el único síntoma era un health check en `Target.Timeout`.
Ahora los puertos salen de `SERVICIOS`, así que sumar un microservicio abre su puerto solo.

**La segunda trampa cara: CORS.** Que el frontend comparta dominio con la API **no** quiere decir
que CORS no intervenga. En un POST el navegador manda la cabecera `Origin` igual, aunque sea mismo
origen. Si ese origen no está declarado en `CORS_ORIGENES`, Spring responde **403 con el cuerpo
`Invalid CORS request` en texto plano**, y lo hace **antes** de la cadena de seguridad: no pasa por
ningún manejador de excepciones, así que no queda rastro en los registros y parece un problema de
token o de permisos. El síntoma que lo delata es que **los GET funcionan y solo fallan los POST**.
`aws.sh desplegar` ahora agrega ese origen solo, derivándolo de `API_ID`.

**Ante un 403 sin rastro, lo primero es mirar el cuerpo de la respuesta**, no los registros.

**La tercera trampa: un bucle en el front que parece un backend lento.** MSAL emite
`ACQUIRE_TOKEN_SUCCESS` en cada token, incluso de caché, con un objeto de cuenta nuevo. Un efecto
que dependía de ese objeto pedía `mi-perfil`, que pedía un token, que cambiaba la cuenta… Llegó a
~500 peticiones por segundo, el BFF pasó de p50 4 ms a 322 ms y la CPU de la tarea a más de 100%.
`App.jsx` solo reemplaza la cuenta si cambia el correo. **Si la app se pone lenta, lo primero es
contar peticiones por ruta en los logs de acceso del API Gateway** (`/aws/apigateway/duocconecta`).

Para que la demo se vea fluida: el JS y el CSS se suben con gzip (S3 y el API Gateway no
comprimen: 627 KB → 172 KB), y el autorizador Lambda cachea el resultado 1 hora, arma la URL del
JWKS sin pasar por el descubrimiento OIDC y precarga las claves al arrancar.

### Cómo se sigue una petición

Todos los contenedores escriben al **mismo** grupo de CloudWatch (`/ecs/duocconecta`), cada uno en
su propia corriente. Para poder distinguir qué línea del BFF corresponde a qué línea del
microservicio, cada petición lleva un **identificador de correlación**:

- `IdDeCorrelacion` (en `common-seguridad`) lo toma de la cabecera `X-Request-Id`, o del
  `X-Amzn-Trace-Id` que inyecta el ALB, o genera uno corto. Lo deja en el MDC y lo devuelve en la
  respuesta, así el navegador puede citarlo al reportar un problema.
- El patrón de log lo imprime en **todas** las líneas: `INFO [a3f19c2b] ...`.
- El BFF **reenvía la cabecera** al llamar a los microservicios, así que una misma petición del
  navegador queda marcada igual en todas las corrientes, **y el identificador también cruza las colas** (§12). Buscar ese identificador en CloudWatch
  devuelve el recorrido completo.

`RegistroDePeticiones` anota **cómo terminó cada petición**: siempre las que fallan (con usuario y
permisos efectivos) y las que superan 1 s aunque hayan salido bien —con el recolector serie y CPU
compartida, una petición lenta es lo que anticipa que el balanceador dé el contenedor por muerto—.
El resto solo en `DEBUG`.

El nivel se sube sin reconstruir la imagen: `LOG_NIVEL=DEBUG make desplegar`.

Corre en **AWS Academy Learner Lab**, lo que impone tres cosas:

- **No se pueden crear roles IAM.** Las tareas usan `LabRole` como `executionRoleArn` y
  `taskRoleArn`. El `ecsTaskExecutionRole` de los tutoriales no existe ahí.
- **Cloud Map no está disponible**, así que los tres servicios comparten una sola tarea de Fargate
  y se comunican por `localhost`. Se pierde el escalado independiente.
- **CloudFront está bloqueado.** Como Azure AD exige HTTPS en los URI de redirección (salvo
  `localhost`) y el sitio de S3 es HTTP puro, el frontend se publica en S3 y se **sirve a través
  del API Gateway**, que da HTTPS. Efecto lateral: front y API comparten dominio.

La contraseña de RDS la gestiona **Secrets Manager**: la base se crea con
`--manage-master-user-password` y la task definition la inyecta por ARN. Nunca existe en el repo.

```bash
make verificar    # qué habilita el lab. Correr primero.
make crear        # ECR, cluster, RDS, ALB, API Gateway. Idempotente.
make desplegar    # construye, sube a ECR y actualiza el servicio
make apagar       # baja las tareas a cero — CORRER AL TERMINAR LA JORNADA
```

El crédito del lab es de **$50 y si se agota se pierde todo el entorno**. El ALB y RDS no se apagan
solos: `make apagar` solo baja las tareas de Fargate.

---

## 12. Mensajería (EP2): Amazon SNS + SQS y RabbitMQ

**Por qué SNS además de SQS.** SQS no tiene exchange: una cola es un buzón punto a punto, sin
routing keys ni comodines. El tópico SNS hace de exchange topic: recibe cada evento una vez y
entrega una copia a cada cola suscrita, según su **filtro** sobre el atributo `tipo`.

```
ms-proyectos ─┐                             filtro (atributo "tipo")        cola SQS                                  consumidor
              ├─► SNS duocconecta-eventos ─┬─ prefix "proyecto."     ─► duocconecta-notificaciones-proyectos      ─► ConsumidorProyectos
ms-contacto ──┘                            ├─ prefix "colaboracion." ─► duocconecta-notificaciones-colaboraciones ─► ConsumidorColaboraciones
                                           └─ sin filtro (el "#")    ─► duocconecta-auditoria-eventos             ─► ConsumidorAuditoria
cada cola ─► <cola>-descartes (DLQ, tras 3 recepciones) · lo que SNS no entrega ─► duocconecta-eventos-no-entregados
```

**Catálogo** (única fuente: `TiposDeEvento`): `proyecto.publicado`, `.actualizado`, `.eliminado`,
`.comentado`, `.colaborador_agregado`, `.ocultado`, `.restaurado`, `.comentario_eliminado`;
`colaboracion.solicitada`, `.aceptada`, `.rechazada`, `.retirada`. En minúsculas y **sin tildes**: el filtro
compara texto exacto y una tilde hace que el evento no le llegue a nadie, sin error.

**Reglas:**

- Los servicios de negocio solo hacen `publishEvent(EventoDuocConecta.nuevo(...))`. La
  publicación en SNS ocurre **después del commit** (`PublicacionTrasConfirmar`); si SNS falla, se
  registra ERROR y el negocio sigue (el outbox queda para la EP3).
- Los eventos llevan **identificadores** (`oid`, UUID), nunca correo, teléfono ni redes.
- Los nombres de tópico, colas, DLQ y filtros viven **solo** en `duocconecta.mensajeria.*` del
  `application.yml` (`PropiedadesMensajeria`). Ningún texto suelto en el código.
- La topología se declara como beans en `ms-notificaciones/config/ConfiguracionTopologia` y
  `DeclaradorTopologia` la crea si falta, sin borrar ni pisar nada. `aws.sh mensajeria` hace lo
  mismo desde la línea de comandos.
- El `X-Request-Id` viaja como atributo del mensaje y el consumidor lo repone en el MDC.

**Confirmación (todos los consumidores en modo `MANUAL`, decisión en `ProcesadorDeMensajes`):**

| Caso | Acción |
|---|---|
| Procesado | `acknowledge()` |
| Duplicado (`evento_procesado`, PK `id_evento` + `consumidor`) | `acknowledge()` sin volver a actuar |
| Tipo nuevo bien formado | `acknowledge()` sin acción: es la gracia del topic |
| Error recuperable | Sin ack + más visibilidad por intento; la redrive lo lleva a la DLQ a la 3.ª recepción |
| No recuperable (JSON inválido, sin `idEvento`/`tipo`, versión no soportada, colaboración sin destinatario) | Copia a su DLQ con el atributo `motivo-descarte` + `acknowledge()` |

Las DLQ **no se consumen**: `MonitorDescartes` las lee sin vaciarlas y `VigilanciaDescartes`
deja en el log cuándo llega algo. Así lo descartado se revisa y se devuelve con el redrive de
`ms-mensajeria`.

**Trampas ya pagadas:**

- SNS y SQS **no aceptan puntos** en el nombre; en el valor del atributo `tipo` sí.
- Sin la política que deja al tópico escribir en la cola, SNS descarta **sin ningún log**.
- Sin **entrega cruda** (`RawMessageDelivery`), el cuerpo llega envuelto y los atributos no
  llegan como atributos SQS.
- En SQS no hay "NACK sin reencolar": lo que no tiene arreglo se copia a la DLQ a mano.
- En SQS lo vencido **se borra**, no va a la DLQ: las DLQ retienen 14 días.
- `spring.cloud.aws.sqs.queue-not-found-strategy: fail`: la estrategia por defecto crearía una
  cola que falta **sin DLQ ni redrive**.
- `create-queue` falla si la cola existe con atributos distintos aunque sea en forma: `aws.sh
  mensajeria` pregunta primero si existe.
- SSE con la clave de SQS (`SqsManagedSseEnabled`): con una clave KMS administrada por AWS, SNS
  no podría escribir.

**Administración (solo `ADMINISTRADOR`):** `ms-mensajeria` en `/api/v1/mensajeria/{colas,topicos,suscripciones}`
y `/api/v1/mensajeria/rabbit/{colas,exchanges,bindings}`
(crear, listar, purgar, redrive, eliminar; la topología base está protegida y responde 409), y
`ms-notificaciones` en `/api/v1/notificaciones/admin/{descartes,consumidores}` (estado de las
DLQ de los dos brokers, y pausa o reanudación de los consumidores para demostrar el
desacoplamiento). **La pausa detiene SQS y RabbitMQ a la vez**: si parara solo uno, el otro
entregaría el mismo evento y el aviso llegaría igual. En RabbitMQ, listar devuelve la topología
base (AMQP no lista colas), y el redrive mueve desde `<cola>.descartes` hasta 100 mensajes por
llamada, confirmando cada uno en la DLQ solo después de publicarlo en su cola. La API de
`ms-mensajeria` solo toca colas con el prefijo `duocconecta-`: una cola ajena responde 400 sin
llegar a AWS.

**Moderación (solo `ADMINISTRADOR`), en `/api/v1/proyectos/admin`:** listar todo, ocultar con
motivo, mostrar, borrar proyectos y borrar comentarios. Ocultar es una marca aparte
(`V4__moderacion.sql`), no un cambio de visibilidad: la dueña no puede revertirla con un PUT.

**Vista de administración (frontend):** `Administracion.jsx`, con las pestañas Resumen
(dashboard de `bff/admin/resumen` más `usuarios/admin/estadisticas` y
`proyectos/admin/estadisticas`), Proyectos (moderación) y Comentarios. Las barras son CSS puro, sin
librerías. El botón solo aparece si `mi-perfil.roles` incluye `ADMINISTRADOR`; la protección real
es el 403 de cada ruta `/admin`.

### 12.1 El segundo broker: RabbitMQ

Los servicios publican cada evento **en los dos brokers** (`PublicadorCompuesto`: si uno falla, el
otro igual publica; solo se propaga el error si fallan todos). El consumidor es el mismo
`ProcesadorDeMensajes`; cada broker cumple la decisión con su `Confirmacion` (`ConfirmacionSqs`,
`ConfirmacionRabbit`). Los consumidores de RabbitMQ usan **el mismo nombre** que los de SQS, así la
tabla `evento_procesado` hace que el evento duplicado entre brokers se procese **una vez**.

```
ms-proyectos ─┐                                    routing key      cola quorum
              ├─► exchange topic duocconecta.eventos ─┬─ proyecto.#     ─► duocconecta.notificaciones.proyectos
ms-contacto ──┘   (routing key = tipo del evento)    ├─ colaboracion.# ─► duocconecta.notificaciones.colaboraciones
                                                     └─ #              ─► duocconecta.auditoria
cada cola ─(rechazo o x-delivery-limit)─► DLX direct duocconecta.dlx ─► <cola>.descartes
```

| Caso | RabbitMQ |
|---|---|
| Procesado o duplicado | `basicAck` |
| Error recuperable | `basicNack(requeue=true)`; la cola quorum cuenta la entrega |
| Tercera entrega fallida | `x-delivery-limit` la manda al DLX → `<cola>.descartes` (`x-first-death-reason = delivery_limit`) |
| No recuperable | `basicNack(requeue=false)` → DLX al tiro (`x-first-death-reason = rejected`) |

**Reglas y trampas:**

- Nombres con **punto** (`duocconecta.`), no guion: en RabbitMQ el punto separa palabras de la
  routing key. La configuración vive en `duocconecta.mensajeria.rabbit.*` del `application.yml`.
- **`x-delivery-limit` cuenta devoluciones, no entregas**: para 3 entregas va en 2
  (`maxEntregas - 1`). Con 3 se ven 4 intentos antes de la DLQ.
- Una cola ya declarada **no acepta otros argumentos**: RabbitMQ responde `PRECONDITION_FAILED`.
  Cambiar el límite exige borrar la cola (en local, con `rabbitmqctl delete_queue`).
- Solo colas **quorum** (`default_queue_type = quorum`): se replican en los dos nodos.
- Credenciales y cookie de Erlang en Secrets Manager (`duocconecta/rabbitmq`); el puerto AMQP solo
  acepta a las tareas de Fargate y la consola, solo la IP de quien corrió `make rabbitmq`.
- `management.health.rabbit.enabled: false`: un RabbitMQ caído no debe hacer que el ALB dé por
  muerto un servicio que sigue atendiendo con SNS/SQS.
- `spring.rabbitmq.connection-timeout: 2s`: se publica después del commit **en el hilo de la
  petición**. Con el valor por defecto (60 s por dirección), la EC2 apagada dejaba colgada cada
  escritura hasta que el API Gateway cortaba a los 30 s, aunque el cambio ya estuviera guardado.
- **Límite conocido del clúster de 2 nodos:** una cola quorum necesita mayoría (2 de 2). Replica los
  datos en los dos nodos, pero si uno cae las colas dejan de aceptar mensajes hasta que vuelve; en
  ese lapso los eventos siguen llegando por SNS/SQS. Tolerar la caída de un nodo exige un tercero.
