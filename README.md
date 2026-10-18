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

Una cuenta que no sea de Duoc no puede entrar, y tampoco se le crea un perfil. La excepción son
las cuentas de **Google** que el equipo anota en `CORREOS_PERMITIDOS` con su rol: usan el botón
**Entrar con Google**, que pasa por Amazon Cognito.

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
| `frontend-web` | 5173 | SPA React + Vite: MSAL (Microsoft) y oidc-client-ts (Google vía Cognito). No es módulo Maven |
| `bff-web` | 8080 | Backend for Frontend: valida el token y agrega respuestas de varios servicios |
| `ms-usuarios` | 8081 | Identidad y perfil. Dueño del schema `usuarios` |
| `ms-proyectos` | 8082 | Vitrina de proyectos y comentarios. Schema `proyectos` |
| `ms-contacto` | 8083 | Solicitudes de colaboración. Schema `contacto` |
| `ms-notificaciones` | 8084 | **EP2.** Consume las colas de SQS y de RabbitMQ y arma la bandeja de avisos. Schema `notificaciones` |
| `ms-mensajeria` | 8085 | **EP2.** Administra colas, tópicos y suscripciones (SNS/SQS) y colas, exchanges y bindings (RabbitMQ). Sin base de datos |
| `common-seguridad` | — | Librería compartida: validación de JWT (audiencia, roles, dominios), manejo de errores y registro de peticiones |
| `common-eventos` | — | **EP2.** Contrato de los eventos, publicación en SNS y en RabbitMQ, y topología de colas |
| `docker` | — | Dockerfile de los seis servicios, inicialización de PostgreSQL y el clúster de RabbitMQ (`docker/rabbitmq`) |
| `infra` | — | Despliegue en AWS: `aws.sh` y la definición de la tarea de ECS |

## Arquitectura

```
Navegador  →  API Gateway  →  ALB  →  BFF  →  microservicios  →  PostgreSQL
              (valida JWT)           (valida JWT)  (valida JWT)
                                                     │  eventos (EP2)
                                                     ▼
                              SNS duocconecta-eventos      → colas SQS      ─┐
                              RabbitMQ duocconecta.eventos → colas quorum   ─┴→ ms-notificaciones
```

El token se valida **tres veces**: en el API Gateway, en el BFF y en el microservicio. Es defensa
en profundidad — quien llegue salteándose una capa igual necesita un token válido.

El frontend solo conoce al BFF. Publicar, editar y borrar un proyecto van directo a `ms-proyectos`:
ahí no hay nada que componer.

## Requisitos

- **Java 21**
- **Maven 3.9+**
- **Docker** con Compose v2 (PostgreSQL y RabbitMQ)
- Un tenant de **Azure AD** con dos registros de aplicación. La guía de configuración del IDaaS se
  mantiene fuera del repositorio: pídela al equipo

## Levantar el proyecto

```bash
cp .env.example .env          # completa AZURE_TENANT_ID y AZURE_CLIENT_ID
docker compose up -d          # PostgreSQL y RabbitMQ (2 nodos)

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
| `COGNITO_ISSUER_URI` · `COGNITO_AUDIENCIA` | No | **EP2.** Login con Google: emisor del user pool de Cognito y su app client. Vacíos = solo Microsoft. `./infra/aws.sh cognito` los muestra |
| `CORS_ORIGENES` | No | Por defecto `http://localhost:5173`. En la nube se le suma el dominio del API Gateway |
| `LOG_NIVEL` | No | Por defecto `INFO`. `DEBUG` registra también las peticiones que salen bien |
| `DB_URL` · `DB_USER` · `DB_PASSWORD` | No | Valores locales por defecto |
| `MS_USUARIOS_URL` · `MS_PROYECTOS_URL` · `MS_CONTACTO_URL` | No | Por defecto `localhost` en sus puertos |
| `S3_BUCKET_ADJUNTOS` | No | Bucket de los archivos adjuntos. Vacío en local: sin él, firmar una subida falla en vez de escribir en un bucket equivocado |
| `AWS_REGION` | No | Por defecto `us-east-1` |
| `SNS_TOPICO_EVENTOS_ARN` | No | **EP2.** Tópico donde se publican los eventos. Vacío = solo se registran en el log. Se obtiene con `make mensajeria` |
| `ADMINISTRADORES` | No | **EP2.** Correos con rol `ADMINISTRADOR`, separados por coma, si no se usa el App Role de Entra. Nunca en el repositorio |
| `CORREOS_PERMITIDOS` | No | **EP2.** Cuentas externas que entran con Google y su rol, en la forma `correo=ROL` separados por coma (por ejemplo `ana@gmail.com=ESTUDIANTE`). Fuera de la lista, un dominio externo sigue recibiendo 403. No acepta `ADMINISTRADOR`: para eso está la variable anterior |
| `CONSUMIDORES_ACTIVOS` | No | **EP2.** En `false`, ms-notificaciones no arranca sus consumidores (sin credenciales de AWS ni RabbitMQ) |
| `RABBITMQ_HABILITADA` | No | **EP2.** Por defecto `true`. En `false`, solo SNS/SQS: no se publica ni se consume en RabbitMQ |
| `RABBITMQ_ADDRESSES` · `RABBITMQ_USUARIO` · `RABBITMQ_CLAVE` | No | **EP2.** Nodos del clúster y credenciales. Por defecto, el clúster local (`localhost:5672,localhost:5673`). En la nube, la clave sale de Secrets Manager |

Las credenciales de AWS **no** son variables: en local salen de `~/.aws/credentials` (panel
*AWS Details* del Learner Lab) y en Fargate de `LabRole`.

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
| `GET /admin/resumen` | **EP2, `ADMINISTRADOR`.** Dashboard: cifras de la comunidad y de la vitrina, con los proyectos contados **por carrera** (cruza ms-proyectos con ms-usuarios) |

`GET /mi-perfil` devuelve además `roles`: los roles efectivos de la sesión, incluido
`ADMINISTRADOR` si se sumó por App Role o por la lista. El frontend lo usa para mostrar la vista
de administración.

### Moderación (EP2, solo `ADMINISTRADOR`) — `/api/v1/proyectos/admin`

| Método y ruta | Qué hace |
|---|---|
| `GET /?oculto=&visibilidad=` | Todos los proyectos, incluidos privados y ocultos |
| `PATCH /{id}/ocultar` · `PATCH /{id}/mostrar` | Saca o devuelve un proyecto de la vitrina. La dueña recibe un aviso con el motivo |
| `DELETE /{id}` | Borra cualquier proyecto, con sus archivos |
| `GET /comentarios?proyectoId=` · `DELETE /comentarios/{id}` | Modera comentarios |
| `GET /estadisticas` | Cifras de la vitrina para el dashboard |

`ms-usuarios` suma `GET /api/v1/usuarios/admin/estadisticas` (perfiles por rol, carrera y sede,
sin datos personales), también solo para `ADMINISTRADOR`.

### Vista de administración (frontend)

Aparece en la cabecera solo si `mi-perfil` trae el rol `ADMINISTRADOR`. Tiene tres pestañas:

| Pestaña | Qué muestra |
|---|---|
| **Resumen** | Perfiles, proyectos, ocultos por moderación, publicados en 30 días y comentarios; barras por rol, carrera, sede, estado y visibilidad |
| **Proyectos** | Todos los proyectos con filtros; ocultar (pide el motivo, que le llega a la dueña), mostrar y eliminar |
| **Comentarios** | Los 50 más recientes, con la opción de eliminarlos |

Ocultar el botón es comodidad: quien protege de verdad es el backend, que responde 403 en cada
ruta `/admin` a cualquier cuenta sin el rol.

### `ms-notificaciones` (EP2) — `/api/v1/notificaciones`

| Método y ruta | Qué hace |
|---|---|
| `GET /` · `GET /no-leidos` | Mis avisos y cuántos no he leído |
| `PATCH /{id}/leido` | Marca un aviso propio |
| `GET /admin/descartes` · `GET /admin/descartes/{cola}/mensajes` | Estado de las DLQ, sin vaciarlas (`ADMINISTRADOR`) |
| `GET /admin/consumidores` · `POST /admin/consumidores/{pausa,reanudar}` | Detiene y reanuda los consumidores sin detener la tarea (`ADMINISTRADOR`) |

### `ms-mensajeria` (EP2, solo `ADMINISTRADOR`) — `/api/v1/mensajeria`

| Método y ruta | Qué hace |
|---|---|
| `GET` · `POST /colas` · `GET` · `DELETE /colas/{nombre}` | Colas SQS; por defecto se crean con su DLQ `<nombre>-descartes` |
| `POST /colas/{nombre}/purga` · `POST /colas/{nombre}/redrive` | Vacía una cola, o devuelve a su cola lo que quedó en una DLQ |
| `GET` · `POST /topicos` · `DELETE /topicos/{nombre}` | Tópicos SNS (los exchanges) |
| `GET` · `POST /suscripciones` · `DELETE /suscripciones/{id}` | Suscripciones con filtro por prefijo de `tipo` (los bindings) |
| `POST /rabbit/colas` · `GET` · `DELETE /rabbit/colas/{nombre}` | Colas quorum de RabbitMQ; por defecto con su DLQ `<nombre>.descartes` enlazada al DLX |
| `POST /rabbit/exchanges` · `DELETE /rabbit/exchanges/{nombre}` | Exchanges `direct`, `topic` o `fanout` |
| `POST` · `DELETE /rabbit/bindings` | Une una cola a un exchange con una routing key o un patrón (`proyecto.#`) |

La topología base de los dos brokers está protegida: eliminarla desde la API responde 409. Y la
API solo toca recursos de la plataforma: `duocconecta-` en AWS y `duocconecta.` en RabbitMQ. Uno
ajeno responde 400 sin llegar al broker.

## Mensajería (EP2)

ms-proyectos y ms-contacto publican **eventos** (`proyecto.comentado`, `colaboracion.aceptada`…)
después de guardar el cambio, **en los dos brokers a la vez**: el tópico **SNS** y el exchange
topic de **RabbitMQ**. Cada uno entrega una copia a sus colas según el filtro o la routing key, y
ms-notificaciones las consume con **confirmación manual**: lo que falla se reintenta y, a la
tercera entrega, queda en su **DLQ**. Como el mismo evento llega dos veces, la tabla
`evento_procesado` hace que se procese **una sola vez**. Los eventos llevan identificadores, nunca
datos de contacto, y el `X-Request-Id` viaja con cada mensaje.

```bash
make mensajeria   # crea lo que falte del tópico, las colas y sus DLQ (idempotente, nunca borra)
make rabbitmq     # EC2 con el clúster de RabbitMQ de 2 nodos (idempotente)
```

El detalle (catálogo de eventos, tabla de confirmación y trampas) está en
[`CLAUDE.md`](CLAUDE.md) §12.

### Replicar la mensajería en otra cuenta de AWS

Cada integrante puede tener la topología en su propio laboratorio. No hace falta crearla a mano
en la consola (y es mejor no hacerlo: un nombre o un filtro distinto deja una cola sin mensajes,
sin ningún error):

1. Credenciales del laboratorio en `~/.aws/credentials` (panel *AWS Details*).
2. `make mensajeria`: crea el tópico, las 3 colas, sus 3 DLQ, la DLQ de no entregados, los
   permisos y las suscripciones con su filtro. Es idempotente.
3. El ARN del tópico se busca solo por nombre al desplegar; en local va en `SNS_TOPICO_EVENTOS_ARN`.

ms-notificaciones, además, verifica la topología al arrancar (`DeclaradorTopologia`) y crea lo que
falte, sin borrar ni pisar nada.

### Dos brokers: SNS/SQS y RabbitMQ

La plataforma publica y consume en **los dos brokers**. El diseño de RabbitMQ (exchange topic más
dead-letter exchange `direct`, colas con DLQ) parte del trabajo de **Javiera** en su repositorio, y
se portó aquí sobre la misma interfaz `PublicadorEventos` y la misma tabla de confirmación.

| | Amazon SNS + SQS | RabbitMQ |
|---|---|---|
| Dónde corre | Servicio administrado de AWS | Clúster de 2 nodos con `docker-compose`: en local y en una EC2 (Amazon MQ no está habilitado en el lab) |
| Exchange | Tópico SNS `duocconecta-eventos` | Exchange `topic` `duocconecta.eventos`; DLX `direct` `duocconecta.dlx` |
| Binding | Suscripción con filtro por prefijo del atributo `tipo` | Binding con routing key `proyecto.#`, `colaboracion.#` y `#` |
| Consumidor | `@SqsListener`, confirmación `MANUAL` | `@RabbitListener`, `ackMode = "MANUAL"` |
| Rechazo sin reintento | Copia a la DLQ + ack (`EnvioADescarte`): SQS no tiene rechazo inmediato | `basicNack(requeue=false)` → dead-letter exchange |
| Límite de reintentos | `RedrivePolicy` con `maxReceiveCount = 3` | Cola quorum con `x-delivery-limit = 2` (cuenta devoluciones: 3 entregas) |

Lo que no cambia entre ambos: entrega **al menos una vez** con idempotencia
(`evento_procesado`), publicar **después del commit**, y separar errores recuperables de no
recuperables.

## Seguridad

Todos los servicios son OAuth2 Resource Server y validan firma, vigencia, emisor y **audiencia**.
Solo `/actuator/health`, `/swagger-ui/**` y `/v3/api-docs/**` quedan abiertos.

Se aceptan **dos emisores**, cada uno con su audiencia y sus claves:

| Login | Emisor | Token | Validación en el gateway |
|---|---|---|---|
| Entrar con Microsoft | Azure AD / Entra ID | access token (`aud` = API) | Autorizador Lambda `duocconecta-autorizador` |
| Entrar con Google | Amazon Cognito (federa a Google) | ID token (`aud` = app client), con `email_verified` | El mismo autorizador Lambda |

En el backend, `DecodificadorMultiEmisor` elige por `iss` y verifica con el decodificador de ese
emisor; un emisor desconocido es 401. En el API Gateway, sin token es 401 y un token inválido, 403.

Los errores salen en **Problem Details (RFC 9457)**, con el motivo en `detail`:

| Código | Cuándo |
|---|---|
| **400** | La petición no tiene sentido |
| **401** | Falta el token, venció o no es válido |
| **403** | Token válido sin permiso: rol, dominio, o la acción es de otra persona |
| **404** | No existe, o su dueño lo ocultó |
| **409** | Choca con el estado actual (ya enviada, ya respondida) |

### Swagger UI

`http://localhost:{8080..8085}/swagger-ui.html` — botón **Authorize** y pegar el access token, sin
la palabra `Bearer`.

## Tests

```bash
mvn test        # 135 pruebas
```

Cubren que el contexto levante, que una ruta protegida responda 401 sin token, que un token sin rol
reciba 403 y no un 500, que un dominio externo reciba 403, y el recorrido completo del
consentimiento en `ms-contacto`: pedir contacto ofreciendo el propio, aceptar, y recién entonces
ver **las dos partes** los datos de la otra.

Las de la EP2 cubren el rol `ADMINISTRADOR` aditivo (y que un dominio externo no se rescata), que
cada acción publique su evento sin datos de contacto, la moderación de la vitrina, cada fila de
la tabla de confirmación de los consumidores (con `Acknowledgement` simulado, sin AWS), la
idempotencia, y la validación del administrador de colas contra un SQS falso (incluido que no
toque colas ajenas). También cubren la lista de correos permitidos para Google, las estadísticas
del dashboard (401 sin token, 403 sin el rol) y el resumen del BFF por carrera. Las de RabbitMQ
cubren la publicación (routing key, cabeceras, sin correos), que un broker caído no corte al otro,
`basicAck` / `basicNack` con y sin reencolar sobre un canal simulado, que el mismo evento por los
dos brokers deje un solo aviso, y el administrador de RabbitMQ contra un `AmqpAdmin` falso.

## Nota sobre la versión de Spring Boot

Se usa **Spring Boot 4.1.1** con Spring Security forzado a **7.1.1**. El enunciado pedía "3.x" y a
la vez "sin CVEs abiertos", y en agosto de 2026 esas dos condiciones son incompatibles: la rama 3.x
está EOL en open source y Spring Security 6.5.x arrastra CVE sin fix público. El razonamiento
completo está en [`CLAUDE.md`](CLAUDE.md) §3.1.
