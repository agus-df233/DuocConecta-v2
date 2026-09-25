# Imagen de cualquiera de los microservicios de DuocConecta.
#
# Es UN solo Dockerfile para los cuatro servicios: cuál se construye lo decide el
# argumento SERVICIO. Se hace así para que no existan cuatro archivos casi idénticos
# que después se desincronizan.
#
#   docker build -f docker/Dockerfile --build-arg SERVICIO=ms-usuarios -t ms-usuarios .
#
# El contexto de build es la raíz del monorepo, porque Maven necesita el pom padre
# y el módulo common-seguridad para compilar cualquiera de los servicios.

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS compilacion
ARG SERVICIO
WORKDIR /build

# Primero solo los poms: si no cambian, Docker reutiliza la capa de dependencias
# y no vuelve a bajar medio Maven Central en cada build.
#
# Van TODOS los módulos y no solo el que se compila: Maven lee el pom padre entero y
# falla si un <module> declarado no está en el contexto, aunque no se vaya a construir.
COPY pom.xml .
COPY common-seguridad/pom.xml common-seguridad/
COPY ms-usuarios/pom.xml      ms-usuarios/
COPY ms-proyectos/pom.xml     ms-proyectos/
COPY ms-contacto/pom.xml      ms-contacto/
COPY bff-web/pom.xml          bff-web/
RUN mvn -q -B dependency:go-offline -pl common-seguridad,ms-usuarios,ms-proyectos,ms-contacto,bff-web || true

# Ahora el código y la compilación real del servicio pedido.
COPY common-seguridad common-seguridad
COPY ${SERVICIO} ${SERVICIO}
RUN mvn -q -B clean package -pl common-seguridad,${SERVICIO} -am -DskipTests \
 && cp ${SERVICIO}/target/*.jar /build/app.jar

# ---------- Etapa 2: separar el jar en capas ----------
# El jar de Spring Boot se descompone para que las dependencias (~80 MB, casi nunca
# cambian) queden en una capa distinta del código de la aplicación (unos pocos MB).
# Sin esto, cada despliegue sube el jar entero a ECR y son minutos perdidos por iteración.
FROM eclipse-temurin:21-jre-alpine AS capas
WORKDIR /capas
COPY --from=compilacion /build/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extraido

# ---------- Etapa 3: imagen final ----------
FROM eclipse-temurin:21-jre-alpine
ARG SERVICIO
WORKDIR /app

# Usuario sin privilegios: si alguien logra ejecutar código en el contenedor,
# no lo hace como root.
RUN addgroup -S duoc && adduser -S duoc -G duoc
USER duoc

# El orden importa: de lo que menos cambia a lo que más.
COPY --from=capas /capas/extraido/dependencies/       ./
COPY --from=capas /capas/extraido/spring-boot-loader/ ./
COPY --from=capas /capas/extraido/snapshot-dependencies/ ./
COPY --from=capas /capas/extraido/application/        ./

ENV SERVICIO=${SERVICIO}
# La JVM no ve el límite de memoria de Fargate salvo que se le diga: sin esto
# reserva heap según la memoria del host y el contenedor muere por OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

ENTRYPOINT ["java", "-jar", "app.jar"]
