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
No evalúa la funcionalidad completa de la plataforma. Todo lo que se construya debe servir a esos
tres puntos.

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

**IDaaS: Azure AD (Microsoft Entra ID)** con flujo **OIDC Authorization Code + PKCE**.
La aplicación nunca almacena contraseñas.

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

La lógica vive **centralizada y configurable** en `ResolvedorRol` + `PropiedadesSeguridad`
(`duocconecta.seguridad.dominios` en `application.yml`), porque pueden sumarse dominios.

**Regla crítica:** el match del dominio es **exacto sobre el texto después del último `@`**, nunca
`endsWith`. Con `endsWith`, `alguien@profesor.duoc.cl` haría match también con `duoc.cl` y quedaría
clasificado mal según el orden de iteración del mapa.

### Fuera de alcance de la EP1

**No generar**: `ms-prompts`, RabbitMQ, Kafka, moderación, notificaciones.
Se difieren a EP2/EP3.

---

## 4. Alcance por fases

| Fase | Qué | Estado |
|---|---|---|
| **1** | Monorepo, `common-seguridad`, `ms-usuarios`, `bff-web`, PostgreSQL, Swagger, tests | Construida |
| **2** | `frontend-web` React + Vite con MSAL (Authorization Code + PKCE) | Construida |
| **3** | `ms-proyectos` (CRUD) + agregación en el BFF → llena la vitrina | Construida |
| **4** | `ms-contacto` (solicitudes de colaboración, HU-17 a HU-20) + agregación en el BFF | Construida |

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
- No generar `ms-prompts`, RabbitMQ, Kafka, moderación ni notificaciones en EP1.
- No exponer entidades JPA directamente.
- `telefono` / `redes` nunca en respuestas públicas.

---

## 7. Estructura de módulos

```
DuocConecta/
├── pom.xml                 # parent (packaging pom), versiones centralizadas
├── docker-compose.yml      # 1 contenedor postgres:16.15-alpine
├── docker/postgres/init.sql
├── docs/azure-entra-id.md  # guía del IDaaS (fuera del repositorio, a propósito)
│
│   # Los módulos, del frontend hacia adentro. En GitLab se ven en orden
│   # alfabético: Git ordena así las entradas del árbol y no se puede cambiar.
├── frontend-web/           # React + Vite (no es módulo Maven)
├── bff-web/                # puerto 8080
├── ms-usuarios/            # puerto 8081, schema `usuarios`
├── ms-proyectos/           # puerto 8082, schema `proyectos`  — vitrina de proyectos
├── ms-contacto/            # puerto 8083, schema `contacto`   — solicitudes de colaboración
├── common-seguridad/       # validación de JWT, manejo de errores y registro, compartidos
├── docker/                 # Dockerfile de los servicios e init.sql de PostgreSQL
└── infra/                  # aws.sh y la definición de la tarea de ECS
```

**Frontend.** Dos pantallas dentro de la aplicación (`Vitrina` y `Perfil`) más el diagnóstico del
token, que vive en el menú de la cuenta. Las solicitudes de colaboración están **dentro de Perfil**
y no en una vista aparte: son datos personales, no de la vitrina. El diseño sigue al prototipo
aprobado y vive en clases de `estilos.css`, nunca en estilos en línea. `catalogo.js` concentra
carreras, sedes, estados e iniciales: es el archivo que se cambia cuando llegue el listado oficial
de carreras de la sede Viña del Mar.

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
| `GET /api/v1/proyectos/{id}/comentarios` | Hilo de comentarios |
| `POST /api/v1/proyectos/{id}/comentarios` | Comenta; comentar no comparte datos de contacto |
| `POST /api/v1/colaboraciones` | Pide contacto a otra persona |
| `PATCH /api/v1/colaboraciones/{id}/responder` | Acepta o rechaza; solo el destinatario |
| `GET /api/v1/colaboraciones/recibidas` | Bandeja de entrada |
| `GET /api/v1/colaboraciones/enviadas` | Bandeja de salida, con los datos ya compartidos |

### Lo que el frontend le pide al BFF y no a los microservicios

| Ruta del BFF | Por qué existe |
|---|---|
| `GET /api/v1/bff/mi-perfil` | Junta el perfil y las redes en una sola llamada |
| `GET /api/v1/bff/vitrina` | Suma a cada proyecto **quién lo publicó** (nombre, carrera, sede), que ms-proyectos no sabe. Es lo que permite filtrar la vitrina por carrera |
| `GET`/`POST /api/v1/bff/vitrina/{id}/comentarios` | Suma el nombre de quien escribió cada comentario |
| `/api/v1/bff/colaboraciones/**` | Al aceptar, el BFF lee el perfil de quien acepta y arma con él los datos de contacto, en vez de pedirle que los escriba a mano |

Publicar, editar y borrar un proyecto sí van directo a `ms-proyectos`: ahí no hay nada que
componer y pasar por el BFF sería una capa de más. La traducción de identificador a persona la
hace `ResolvedorAutores`, que consulta `GET /api/v1/usuarios/por-oid/{oid}` una vez por persona y
no una por fila, y **no cachea entre peticiones**: un dato guardado de más sobreviviría a que
alguien se ocultara.

### Modelo de proyecto

`resumen` es la línea que se lee en la tarjeta; `descripcion` es el detalle completo.
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
| Puerto | `ms-usuarios` 8081 · `ms-proyectos` 8082 · `ms-contacto` 8083; el siguiente, 8084 |
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
Navegador          API Gateway HTTP API      ALB          ECS Fargate — 1 tarea, 4 contenedores
   (front)     →   (validación de JWT)   →  (rutas)  →    ├── bff-web      :8080
                                                          ├── ms-usuarios  :8081  → RDS PostgreSQL
                                                          ├── ms-proyectos :8082
                                                          └── ms-contacto  :8083
```

Con cuatro JVM en la misma tarea hay tres cosas que **no se ajustan solas** y que costaron un
despliegue entero de depuración:

| Qué | Valor | Por qué |
|---|---|---|
| Memoria | 2 vCPU / 4 GB de tarea, tope de 1 GB por contenedor | El heap lo acota la imagen con `MaxRAMPercentage`. Sin topes, la primera JVM en arrancar se queda con casi toda la RAM y las demás mueren |
| Período de gracia del health check | **420 s**, no los 240 s iniciales | ECS mata la tarea entera si **cualquiera** de los cuatro target groups la ve enferma al vencer la gracia. Con tres servicios corriendo Flyway al arrancar, la última en levantar se llevaba puestas a las otras tres |
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

### Cómo se sigue una petición

Los cuatro contenedores escriben al **mismo** grupo de CloudWatch (`/ecs/duocconecta`), cada uno en
su propia corriente. Para poder distinguir qué línea del BFF corresponde a qué línea del
microservicio, cada petición lleva un **identificador de correlación**:

- `IdDeCorrelacion` (en `common-seguridad`) lo toma de la cabecera `X-Request-Id`, o del
  `X-Amzn-Trace-Id` que inyecta el ALB, o genera uno corto. Lo deja en el MDC y lo devuelve en la
  respuesta, así el navegador puede citarlo al reportar un problema.
- El patrón de log lo imprime en **todas** las líneas: `INFO [a3f19c2b] ...`.
- El BFF **reenvía la cabecera** al llamar a los microservicios, así que una misma petición del
  navegador queda marcada igual en las cuatro corrientes. Buscar ese identificador en CloudWatch
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
