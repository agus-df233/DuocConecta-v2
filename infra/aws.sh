#!/usr/bin/env bash
#
# aws.sh — despliegue de DuocConecta en AWS Academy Learner Lab.
#
# Un solo script con subcomandos, en vez de seis archivos que se desincronizan:
#
#   ./infra/aws.sh verificar   Qué servicios habilita el lab y si LabRole alcanza. Correr primero.
#   ./infra/aws.sh crear       Crea ECR, cluster, security groups, RDS, ALB y API Gateway. Idempotente.
#   ./infra/aws.sh build       Compila la imagen de un servicio y la sube a ECR.
#   ./infra/aws.sh desplegar   Registra la task definition y actualiza el servicio de ECS.
#   ./infra/aws.sh front       Construye el frontend y lo publica en S3, detrás del API Gateway.
#   ./infra/aws.sh apigw       Rutas por microservicio, validación de JWT y CORS en el API Gateway.
#   ./infra/aws.sh iniciar     Levanta la tarea, espera el health y muestra las URLs.
#   ./infra/aws.sh apagar      Baja la tarea a cero. IMPORTANTE: correrlo al terminar de trabajar.
#   ./infra/aws.sh urls        Muestra las URLs del ALB y del API Gateway.
#   ./infra/aws.sh mensajeria  Crea lo que falte del tópico SNS, las colas SQS y sus DLQ (EP2). Idempotente.
#   ./infra/aws.sh cognito     Login con Google: user pool, dominio, app client y Google como IdP. Idempotente.
#   ./infra/aws.sh rabbitmq    RabbitMQ en una EC2: un nodo con docker-compose. Idempotente.
#
# Las credenciales del lab van en ~/.aws/credentials (panel "AWS Details"), nunca en el repo:
# rotan en cada sesión.

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
PROYECTO="duocconecta"
CLUSTER="$PROYECTO"
SERVICIO_ECS="$PROYECTO"
FAMILIA="$PROYECTO"
SERVICIOS=(ms-usuarios bff-web ms-proyectos ms-contacto ms-notificaciones ms-mensajeria)

# Servicios que corren en su propia tarea y su propio servicio de ECS. ECS admite como máximo
# 5 target groups por servicio y ya son 6. ms-mensajeria puede ir aparte porque no usa la base
# ni llama a los demás por localhost: solo habla con SQS y SNS.
SERVICIOS_APARTE=(ms-mensajeria)

es_aparte() { local a; for a in "${SERVICIOS_APARTE[@]}"; do [[ "$a" == "$1" ]] && return 0; done; return 1; }

# Los servicios de ECS que hay que encender y apagar: el principal y uno por cada servicio aparte.
servicios_ecs() { echo "$SERVICIO_ECS"; local a; for a in "${SERVICIOS_APARTE[@]}"; do echo "$SERVICIO_ECS-$a"; done; }

# Etiqueta de imagen de la v2. No es "latest" a propósito: así las imágenes de la EP1 siguen en
# ECR sin pisarse, y su task definition anterior sirve para volver atrás.
TAG="${TAG:-ep2}"

# Los nombres se declaran acá y no se repiten sueltos por el script. Están pensados para que
# quien mire la consola de AWS entienda de una qué papel cumple cada recurso: la evaluación
# pide mostrar "la instancia de API Manager", así que se llama así y no con una sigla.
API_NOMBRE="$PROYECTO-api-manager"
AUTH_NOMBRE="validador-jwt-entra-id"   # el autorizador de un solo emisor, que se retira
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Puerto y ruta de cada servicio. Se resuelven con funciones y no con arreglos asociativos
# porque macOS trae bash 3.2, que no los soporta: en esa versión el script ni siquiera arranca.
puerto_de() {
  case "$1" in
    bff-web)      echo 8080 ;;
    ms-usuarios)  echo 8081 ;;
    ms-proyectos) echo 8082 ;;
    ms-contacto)  echo 8083 ;;
    ms-notificaciones) echo 8084 ;;
    ms-mensajeria)     echo 8085 ;;
  esac
}

# Path por el que el ALB enruta hacia cada servicio.
ruta_de() {
  case "$1" in
    bff-web)      echo "/api/v1/bff*" ;;
    ms-usuarios)  echo "/api/v1/usuarios*" ;;
    ms-proyectos) echo "/api/v1/proyectos*" ;;
    ms-contacto)  echo "/api/v1/colaboraciones*" ;;
    ms-notificaciones) echo "/api/v1/notificaciones*" ;;
    ms-mensajeria)     echo "/api/v1/mensajeria*" ;;
  esac
}

azul()  { printf '\033[1;34m%b\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
falla() { printf '  \033[31m✗\033[0m %s\n' "$*"; }
aviso() { printf '  \033[33m!\033[0m %s\n' "$*"; }

# Carga .env si existe, para tomar la configuración local sin pedirla cada vez.
[[ -f "$RAIZ/.env" ]] && set -a && source "$RAIZ/.env" && set +a

cuenta() { aws sts get-caller-identity --query Account --output text; }

# Datos de Cognito (login con Google). Opcionales: sin user pool, la plataforma funciona solo
# con Microsoft. No son secretos: el emisor y el client id viajan dentro de cada token.
POOL_NOMBRE="$PROYECTO-usuarios"
CLIENTE_NOMBRE="$PROYECTO-web"
datos_cognito() {
  local pool cliente
  pool="${COGNITO_POOL_ID:-}"
  [[ -z "$pool" ]] && pool=$(aws cognito-idp list-user-pools --max-results 60 --output json 2>/dev/null \
    | jq -r --arg n "$POOL_NOMBRE" '.UserPools[] | select(.Name==$n) | .Id' | head -1)
  if [[ -z "$pool" ]]; then
    COGNITO_ISSUER_URI=""; COGNITO_AUDIENCIA=""; COGNITO_DOMINIO=""
    aviso "Sin user pool de Cognito: solo login con Microsoft (corre 'aws.sh cognito' para Google)"
    return 0
  fi
  cliente="${COGNITO_CLIENT_ID:-}"
  [[ -z "$cliente" ]] && cliente=$(aws cognito-idp list-user-pool-clients --user-pool-id "$pool" --output json \
    | jq -r --arg n "$CLIENTE_NOMBRE" '.UserPoolClients[] | select(.ClientName==$n) | .ClientId' | head -1)
  COGNITO_POOL_ID="$pool"
  COGNITO_AUDIENCIA="$cliente"
  COGNITO_DOMINIO="${COGNITO_DOMINIO:-$PROYECTO-$(cuenta).auth.$REGION.amazoncognito.com}"
  # El emisor tiene que coincidir EXACTO con el claim 'iss' del token, sin barra final.
  COGNITO_ISSUER_URI="https://cognito-idp.$REGION.amazonaws.com/$pool"
}

# RabbitMQ en la nube: una EC2 con un nodo en docker-compose (Amazon MQ no está
# habilitado en el lab). Sin la EC2, la plataforma funciona solo con SNS/SQS.
RABBIT_NOMBRE="$PROYECTO-rabbitmq"
# Versión fija de docker compose en la EC2: con "latest", dos despliegues podían terminar distintos.
COMPOSE_VERSION="v2.29.7"
RABBIT_SECRETO="$PROYECTO/rabbitmq"
id_ec2_rabbit() {
  aws ec2 describe-instances --filters "Name=tag:Name,Values=$RABBIT_NOMBRE" \
    "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null | awk '{print $1}'
}
datos_rabbit() {
  local id; id=$(id_ec2_rabbit)
  # Sin EC2 queda deshabilitado; la dirección es solo para que Spring tenga un valor que leer.
  RABBITMQ_HABILITADA=false; RABBITMQ_ADDRESSES="localhost:5672"; SECRETO_RABBIT_ARN=""
  if [[ -z "$id" ]]; then
    aviso "Sin EC2 de RabbitMQ: solo SNS/SQS (corre 'aws.sh rabbitmq' para el segundo broker)"
    return 0
  fi
  local ip; ip=$(aws ec2 describe-instances --instance-ids "$id" --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
  SECRETO_RABBIT_ARN=$(aws secretsmanager describe-secret --secret-id "$RABBIT_SECRETO" --query ARN --output text)
  RABBITMQ_ADDRESSES="$ip:5672"
  RABBITMQ_HABILITADA=true
}

exigir_azure() {
  [[ -z "${AZURE_TENANT_ID:-}" || -z "${AZURE_CLIENT_ID:-}" ]] && {
    falla "Faltan AZURE_TENANT_ID o AZURE_CLIENT_ID (login con Microsoft). Copia .env.example a .env y complétalos."
    exit 1
  }
  return 0
}
registro() { echo "$(cuenta).dkr.ecr.$REGION.amazonaws.com"; }

# Guarda CLAVE=VALOR en infra/.recursos: reemplaza la línea si ya está y la agrega si falta. Antes
# 'crear' reescribía el archivo entero y se perdían el bucket de adjuntos, Cognito y el sitio web.
guardar_recurso() { # $1 = clave, $2 = valor
  local archivo="$RAIZ/infra/.recursos"
  touch "$archivo"
  if grep -q "^$1=" "$archivo"; then
    sed -i '' "s|^$1=.*|$1=$2|" "$archivo"
  else
    echo "$1=$2" >> "$archivo"
  fi
}

# ---------------------------------------------------------------------------
# verificar — qué permite realmente el lab. Correr esto antes que nada.
# ---------------------------------------------------------------------------
cmd_verificar() {
  azul "Región: $REGION"
  aws sts get-caller-identity --output table || { falla "Sin credenciales. Pegá el bloque de AWS Details en ~/.aws/credentials"; exit 1; }

  azul "\nRol LabRole (obligatorio: en el lab no se pueden crear roles IAM)"
  if aws iam get-role --role-name LabRole >/dev/null 2>&1; then
    ok "LabRole existe"
  else
    falla "LabRole no existe. Sin él las tareas de Fargate no pueden arrancar."
  fi

  azul "\nServicios que usa el despliegue"
  probar() { # $1 = nombre legible, $2... = comando
    local nombre="$1"; shift
    if "$@" >/dev/null 2>&1; then ok "$nombre"; else falla "$nombre — NO disponible"; fi
  }
  probar "ECR"             aws ecr describe-repositories --max-items 1
  probar "ECS"             aws ecs list-clusters --max-items 1
  probar "RDS"             aws rds describe-db-instances --max-items 1
  probar "ALB"             aws elbv2 describe-load-balancers --page-size 1
  probar "Secrets Manager" aws secretsmanager list-secrets --max-results 1
  probar "API Gateway"     aws apigatewayv2 get-apis --max-results 1
  probar "CloudWatch Logs" aws logs describe-log-groups --limit 1
  probar "SNS (EP2)"       aws sns list-topics
  probar "SQS (EP2)"       aws sqs list-queues --max-results 1
  probar "EC2 (RabbitMQ)"  aws ec2 describe-instances --max-items 1
  probar "SSM (entrar a la EC2)" aws ssm describe-instance-information --max-items 1
  probar "Lambda (autorizador)"  aws lambda list-functions --max-items 1
  probar "Cognito (Google)" aws cognito-idp list-user-pools --max-results 1

  azul "\nSegundo broker"
  local rabbit; rabbit=$(id_ec2_rabbit)
  if [[ -n "$rabbit" ]]; then
    ok "EC2 $RABBIT_NOMBRE: $(aws ec2 describe-instances --instance-ids "$rabbit" \
      --query 'Reservations[0].Instances[0].State.Name' --output text)"
  else
    aviso "Sin EC2 de RabbitMQ: la plataforma usará solo SNS/SQS (corre 'aws.sh rabbitmq')"
  fi

  azul "\nRecordatorio de costo"
  aviso "ALB y RDS no se apagan solos: 'aws.sh apagar' baja las tareas, pero el ALB sigue cobrando."
  aviso "Mira el crédito consumido en el panel del Learner Lab todos los días."
}

# ---------------------------------------------------------------------------
# crear — infraestructura. Idempotente: se puede correr las veces que haga falta.
# ---------------------------------------------------------------------------
cmd_crear() {
  local acc; acc=$(cuenta)

  azul "1/6 · Repositorios de ECR"
  for s in "${SERVICIOS[@]}"; do
    aws ecr describe-repositories --repository-names "$PROYECTO/$s" >/dev/null 2>&1 \
      || aws ecr create-repository --repository-name "$PROYECTO/$s" >/dev/null
    ok "$PROYECTO/$s"
  done

  azul "2/6 · Cluster de ECS y grupo de logs"
  aws ecs describe-clusters --clusters "$CLUSTER" --query 'clusters[0].status' --output text 2>/dev/null | grep -q ACTIVE \
    || aws ecs create-cluster --cluster-name "$CLUSTER" >/dev/null
  # Sin --capacity-providers a propósito: ese flag exige permisos sobre el service-linked role
  # que el Learner Lab no concede. Fargate funciona igual porque el servicio se crea
  # más abajo con --launch-type FARGATE.
  aws logs create-log-group --log-group-name "/ecs/$PROYECTO" 2>/dev/null || true
  ok "cluster $CLUSTER"

  azul "3/6 · Red y security groups"
  local vpc subnets sg_alb sg_tareas sg_rds
  vpc=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)
  subnets=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$vpc" --query 'Subnets[].SubnetId' --output text | tr '\t' ',')
  ok "VPC $vpc"

  crear_sg() { # $1 = nombre, $2 = descripción
    local id
    id=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$1" "Name=vpc-id,Values=$vpc" \
         --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null)
    if [[ "$id" == "None" || -z "$id" ]]; then
      id=$(aws ec2 create-security-group --group-name "$1" --description "$2" --vpc-id "$vpc" --query GroupId --output text)
    fi
    echo "$id"
  }
  sg_alb=$(crear_sg "$PROYECTO-alb" "Entrada HTTP publica al ALB de DuocConecta")
  sg_tareas=$(crear_sg "$PROYECTO-tareas" "Tareas de Fargate; solo aceptan trafico del ALB")
  sg_rds=$(crear_sg "$PROYECTO-rds" "PostgreSQL; solo acepta trafico de las tareas")

  # El ALB acepta HTTP de cualquiera; las tareas solo del ALB; la base solo de las tareas.
  aws ec2 authorize-security-group-ingress --group-id "$sg_alb" --protocol tcp --port 80 --cidr 0.0.0.0/0 >/dev/null 2>&1 || true
  # Los puertos salen de SERVICIOS, no de una lista a mano: al sumar ms-contacto su puerto
  # quedó fuera y el ALB no lo alcanzaba. Síntoma engañoso: el contenedor arrancaba bien y el
  # health check daba timeout, porque los paquetes morían en el security group.
  for s_puerto in "${SERVICIOS[@]}"; do
    p=$(puerto_de "$s_puerto")
    aws ec2 authorize-security-group-ingress --group-id "$sg_tareas" --protocol tcp --port "$p" --source-group "$sg_alb" >/dev/null 2>&1 || true
  done
  aws ec2 authorize-security-group-ingress --group-id "$sg_rds" --protocol tcp --port 5432 --source-group "$sg_tareas" >/dev/null 2>&1 || true
  ok "SGs: alb=$sg_alb tareas=$sg_tareas rds=$sg_rds"

  azul "4/6 · Base de datos RDS"
  # --manage-master-user-password deja la contraseña en Secrets Manager: nunca pasa por acá.
  if ! aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" >/dev/null 2>&1; then
    aws rds create-db-instance \
      --db-instance-identifier "$PROYECTO-db" \
      --db-name duocconecta --engine postgres --engine-version 16.15 \
      --db-instance-class db.t3.micro --allocated-storage 20 \
      --master-username duocconecta --manage-master-user-password \
      --vpc-security-group-ids "$sg_rds" \
      --no-publicly-accessible --backup-retention-period 0 --no-multi-az >/dev/null
    aviso "RDS creándose (tarda ~8 min). El resto sigue."
  fi
  ok "$PROYECTO-db"

  azul "5/6 · ALB y target groups"
  local alb_arn listener_arn
  alb_arn=$(aws elbv2 describe-load-balancers --names "$PROYECTO-alb" --query 'LoadBalancers[0].LoadBalancerArn' --output text 2>/dev/null || true)
  if [[ -z "$alb_arn" || "$alb_arn" == "None" ]]; then
    alb_arn=$(aws elbv2 create-load-balancer --name "$PROYECTO-alb" --type application --scheme internet-facing \
      --subnets ${subnets//,/ } --security-groups "$sg_alb" --query 'LoadBalancers[0].LoadBalancerArn' --output text)
  fi

  # Un target group por servicio: así se puede pegar directo a /api/v1/usuarios y ver el 401,
  # que es la demostración de que cada capa valida el token por su cuenta.
  local prioridad=10
  for s in "${SERVICIOS[@]}"; do
    local tg
    tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
    if [[ -z "$tg" || "$tg" == "None" ]]; then
      tg=$(aws elbv2 create-target-group --name "$PROYECTO-$s" --protocol HTTP --port "$(puerto_de "$s")" \
        --vpc-id "$vpc" --target-type ip --health-check-path /actuator/health \
        --health-check-interval-seconds 30 --health-check-timeout-seconds 15 \
        --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
        --query 'TargetGroups[0].TargetGroupArn' --output text)
    fi
    # Timeout de 15s y no los 5 por defecto: con cuatro JVM en 2 vCPU y recolector serie, una
    # pausa de GC hace que /actuator/health tarde más de 5s y el ALB da el destino por muerto.
    aws elbv2 modify-target-group --target-group-arn "$tg" \
      --health-check-timeout-seconds 15 --health-check-interval-seconds 30 \
      --healthy-threshold-count 2 --unhealthy-threshold-count 3 >/dev/null 2>&1 || true
    ok "target group $PROYECTO-$s → :$(puerto_de "$s")"
  done

  # El listener manda por defecto al BFF, que es lo único que el frontend conoce.
  local tg_bff; tg_bff=$(aws elbv2 describe-target-groups --names "$PROYECTO-bff-web" --query 'TargetGroups[0].TargetGroupArn' --output text)
  listener_arn=$(aws elbv2 describe-listeners --load-balancer-arn "$alb_arn" --query 'Listeners[0].ListenerArn' --output text 2>/dev/null || true)
  if [[ -z "$listener_arn" || "$listener_arn" == "None" ]]; then
    listener_arn=$(aws elbv2 create-listener --load-balancer-arn "$alb_arn" --protocol HTTP --port 80 \
      --default-actions "Type=forward,TargetGroupArn=$tg_bff" --query 'Listeners[0].ListenerArn' --output text)
  fi
  # La lista sale de SERVICIOS y no se escribe a mano: con una lista fija, un servicio nuevo
  # quedaba sin regla y el único síntoma era un health check en Target.Timeout.
  for s in "${SERVICIOS[@]}"; do
    [[ "$s" == "bff-web" ]] && continue
    local tg; tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text)
    aws elbv2 create-rule --listener-arn "$listener_arn" --priority "$prioridad" \
      --conditions "Field=path-pattern,Values=$(ruta_de "$s")" \
      --actions "Type=forward,TargetGroupArn=$tg" >/dev/null 2>&1 || true
    prioridad=$((prioridad + 10))
  done
  ok "ALB listo"

  azul "6/6 · API Gateway (capa API Manager)"
  local dns api_id
  dns=$(aws elbv2 describe-load-balancers --load-balancer-arns "$alb_arn" --query 'LoadBalancers[0].DNSName' --output text)
  api_id=$(aws apigatewayv2 get-apis --query "Items[?Name=='$API_NOMBRE'].ApiId" --output text)
  if [[ -z "$api_id" ]]; then
    api_id=$(aws apigatewayv2 create-api --name "$API_NOMBRE" --protocol-type HTTP \
      --target "http://$dns" --query ApiId --output text)
  fi
  ok "API Gateway $api_id"

  # Se guardan los identificadores para que 'desplegar' e 'iniciar' no los busquen de nuevo. Lo
  # que guardaron otros comandos (bucket de adjuntos, tópico, Cognito) se conserva.
  guardar_recurso VPC "$vpc"
  guardar_recurso SUBNETS "$subnets"
  guardar_recurso SG_TAREAS "$sg_tareas"
  guardar_recurso ALB_ARN "$alb_arn"
  guardar_recurso ALB_DNS "$dns"
  guardar_recurso API_ID "$api_id"
  aviso "Identificadores guardados en infra/.recursos (ignorado por git)"
  cmd_urls
}

# ---------------------------------------------------------------------------
# build — compila la imagen de un servicio y la sube a ECR.
# ---------------------------------------------------------------------------
cmd_build() {
  local s="${1:?Uso: aws.sh build <ms-usuarios|bff-web|ms-proyectos|ms-contacto|ms-notificaciones|ms-mensajeria>}"
  [[ -d "$RAIZ/$s" ]] || { falla "El módulo $s todavía no existe en el repo"; exit 1; }
  local reg; reg=$(registro)

  aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$reg" >/dev/null
  # Se usa buildx y no 'docker build' porque hace falta fijar la arquitectura: Fargate corre
  # x86_64 y los Mac con chip M son ARM. Sin esto la imagen no arranca en la nube.
  # buildx construye y sube en un solo paso.
  docker buildx build --platform linux/amd64 --push \
    -f "$RAIZ/docker/Dockerfile" --build-arg "SERVICIO=$s" \
    -t "$reg/$PROYECTO/$s:$TAG" "$RAIZ"
  ok "$s publicado en ECR ($TAG)"
}

# ---------------------------------------------------------------------------
# desplegar — renderiza la task definition, la registra y actualiza el servicio.
# ---------------------------------------------------------------------------
cmd_desplegar() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  local acc reg secreto endpoint tmp
  acc=$(cuenta); reg=$(registro)

  endpoint=$(aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" \
    --query 'DBInstances[0].Endpoint.Address' --output text)
  secreto=$(aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" \
    --query 'DBInstances[0].MasterUserSecret.SecretArn' --output text)
  [[ "$endpoint" == "None" ]] && { falla "RDS todavía no terminó de crearse"; exit 1; }

  # Sin estos valores el servicio arranca con un emisor vacío y rechaza todos los tokens,
  # pero el error recién aparece en los logs varios minutos después. Mejor fallar acá.
  exigir_azure
  datos_cognito
  datos_rabbit

  # El front se sirve desde el mismo dominio del API Gateway. En un POST el navegador manda
  # igual la cabecera Origin, aunque sea mismo origen, y si ese origen no está declarado el BFF
  # responde 403 "Invalid CORS request" antes de mirar el token. Se agrega solo, derivado del
  # API_ID, para que no dependa de que alguien lo escriba a mano en .env.
  local origenes="${CORS_ORIGENES:-http://localhost:5173}"
  if [[ -n "${API_ID:-}" && "$origenes" != *"$API_ID.execute-api.$REGION.amazonaws.com"* ]]; then
    origenes="$origenes,https://$API_ID.execute-api.$REGION.amazonaws.com"
  fi

  # El ARN del tópico lo guarda 'aws.sh mensajeria' en .recursos. Si falta (por ejemplo, porque
  # 'crear' reescribió el archivo), se busca en SNS. Sin él, los eventos solo se registran.
  local topico_arn="${TOPICO_EVENTOS_ARN:-}"
  if [[ -z "$topico_arn" ]]; then
    topico_arn=$(aws sns list-topics --query "Topics[?ends_with(TopicArn, ':$PROYECTO-eventos')].TopicArn" \
      --output text 2>/dev/null || true)
  fi
  [[ -z "$topico_arn" || "$topico_arn" == "None" ]] && {
    aviso "No existe el tópico $PROYECTO-eventos: corre 'aws.sh mensajeria'. Los eventos solo se registrarán."
    topico_arn=""
  }

  tmp=$(mktemp)  # archivo temporal: la task definition renderizada lleva ARNs y no va al repo
  sed -e "s|__ACCOUNT_ID__|$acc|g" -e "s|__REGISTRO__|$reg|g" -e "s|__TAG__|$TAG|g" \
      -e "s|__REGION__|$REGION|g" -e "s|__RDS_ENDPOINT__|$endpoint|g" \
      -e "s|__SECRETO_DB_ARN__|$secreto|g" \
      -e "s|__AZURE_TENANT_ID__|${AZURE_TENANT_ID:-}|g" \
      -e "s|__AZURE_CLIENT_ID__|${AZURE_CLIENT_ID:-}|g" \
      -e "s|__COGNITO_ISSUER_URI__|$COGNITO_ISSUER_URI|g" \
      -e "s|__COGNITO_AUDIENCIA__|$COGNITO_AUDIENCIA|g" \
      -e "s|__CORS_ORIGENES__|$origenes|g" \
      -e "s|__LOG_NIVEL__|${LOG_NIVEL:-INFO}|g" \
      -e "s|__BUCKET_ADJUNTOS__|${BUCKET_ADJUNTOS:-}|g" \
      -e "s|__TOPICO_EVENTOS_ARN__|$topico_arn|g" \
      -e "s|__ADMINISTRADORES__|${ADMINISTRADORES:-}|g" \
      -e "s|__CORREOS_PERMITIDOS__|${CORREOS_PERMITIDOS:-}|g" \
      -e "s|__RABBITMQ_HABILITADA__|$RABBITMQ_HABILITADA|g" \
      -e "s|__RABBITMQ_ADDRESSES__|$RABBITMQ_ADDRESSES|g" \
      -e "s|__SECRETO_RABBIT_ARN__|$SECRETO_RABBIT_ARN|g" \
      "$RAIZ/infra/task-definition.json" > "$tmp"

  # Se quita el bloque de comentarios (ECS rechaza campos que no conoce) y, si la imagen de
  # algún servicio todavía no está en ECR, se quita ese contenedor para no bloquear el despliegue:
  # el contenedor es essential, así que sin imagen la tarea entera no arrancaría.
  local filtro='del(._comentario)'
  # Sin EC2 de RabbitMQ no hay secreto que leer: esas entradas harían fallar el arranque de la tarea.
  [[ -z "$SECRETO_RABBIT_ARN" ]] && filtro="$filtro | .containerDefinitions |= map(if .secrets then .secrets |= map(select(.name | startswith(\"RABBITMQ_\") | not)) else . end)"
  for s in "${SERVICIOS[@]}"; do
    if ! aws ecr describe-images --repository-name "$PROYECTO/$s" --image-ids "imageTag=$TAG" >/dev/null 2>&1; then
      aviso "$s aún no está en ECR: se despliega sin ese contenedor"
      filtro="$filtro | .containerDefinitions |= map(select(.name != \"$s\"))"
    fi
  done
  jq "$filtro" "$tmp" > "$tmp.json" && mv "$tmp.json" "$tmp"

  # Cada servicio aparte sale a su propia task definition, chica y sin dependencias de arranque.
  local s
  for s in "${SERVICIOS_APARTE[@]}"; do
    jq --arg s "$s" --arg f "$FAMILIA-$s" '.family = $f | .cpu = "512" | .memory = "1024"
      | .containerDefinitions |= map(select(.name == $s) | del(.dependsOn))' "$tmp" > "$tmp.$s"
    jq --arg s "$s" '.containerDefinitions |= map(select(.name != $s))' "$tmp" > "$tmp.json" && mv "$tmp.json" "$tmp"
  done

  local rev; rev=$(aws ecs register-task-definition --cli-input-json "file://$tmp" \
    --query 'taskDefinition.taskDefinitionArn' --output text)
  rm -f "$tmp"
  ok "task definition registrada: ${rev##*/}"

  # Cada target group registrado apunta a su contenedor dentro de la misma tarea.
  local lb_args=()
  for s in "${SERVICIOS[@]}"; do
    es_aparte "$s" && continue
    local tg; tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
    [[ -z "$tg" || "$tg" == "None" ]] && continue
    [[ -d "$RAIZ/$s" ]] || continue
    lb_args+=("targetGroupArn=$tg,containerName=$s,containerPort=$(puerto_de "$s")")
  done

  # Sin período de gracia, ECS mata la tarea por "unhealthy" antes de que Spring Boot levante.
  # Son 10 minutos: ECS mata la tarea entera si CUALQUIERA de los target groups la ve enferma al
  # vencer, y con seis JVM y cuatro Flyway en 2 vCPU los 7 minutos de la EP1 quedan justos.
  if aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICIO_ECS" \
       --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE; then
    # Los balanceadores se redeclaran en cada actualización: al sumar un microservicio, su
    # target group existe pero el servicio seguía registrando solo los viejos.
    aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --force-new-deployment \
      --health-check-grace-period-seconds 600 \
      --load-balancers "${lb_args[@]}" >/dev/null
    ok "servicio actualizado (${#lb_args[@]} servicios tras el balanceador)"
  else
    aws ecs create-service --cluster "$CLUSTER" --service-name "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_TAREAS],assignPublicIp=ENABLED}" \
      --health-check-grace-period-seconds 600 \
      --load-balancers "${lb_args[@]}" >/dev/null
    ok "servicio creado"
  fi

  for s in "${SERVICIOS_APARTE[@]}"; do
    [[ -s "$tmp.$s" ]] || continue
    if ! jq -e '.containerDefinitions | length > 0' "$tmp.$s" >/dev/null; then
      rm -f "$tmp.$s"; continue   # su imagen no estaba en ECR
    fi
    desplegar_aparte "$s" "$tmp.$s"
    rm -f "$tmp.$s"
  done
  cmd_urls
}

# Registra la task definition de un servicio aparte y crea o actualiza su servicio de ECS.
desplegar_aparte() { # $1 = servicio, $2 = task definition renderizada
  local s="$1" archivo="$2" rev_s tg servicio="$SERVICIO_ECS-$1"
  rev_s=$(aws ecs register-task-definition --cli-input-json "file://$archivo" \
    --query 'taskDefinition.taskDefinitionArn' --output text)
  ok "task definition registrada: ${rev_s##*/}"
  tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text)
  if aws ecs describe-services --cluster "$CLUSTER" --services "$servicio" \
       --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE; then
    aws ecs update-service --cluster "$CLUSTER" --service "$servicio" \
      --task-definition "$rev_s" --desired-count 1 --force-new-deployment \
      --health-check-grace-period-seconds 300 \
      --load-balancers "targetGroupArn=$tg,containerName=$s,containerPort=$(puerto_de "$s")" >/dev/null
    ok "servicio $servicio actualizado"
  else
    aws ecs create-service --cluster "$CLUSTER" --service-name "$servicio" \
      --task-definition "$rev_s" --desired-count 1 --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_TAREAS],assignPublicIp=ENABLED}" \
      --health-check-grace-period-seconds 300 \
      --load-balancers "targetGroupArn=$tg,containerName=$s,containerPort=$(puerto_de "$s")" >/dev/null
    ok "servicio $servicio creado"
  fi
}

# ---------------------------------------------------------------------------
# front — publica el frontend en S3 y lo deja accesible por HTTPS.
#
# Pasa por el API Gateway y no se sirve S3 directo porque Azure AD y Cognito exigen HTTPS en los
# URI de redirección (salvo localhost), S3 es HTTP puro y CloudFront no está habilitado. El API Gateway da HTTPS y deja
# el front y la API en el mismo origen, así el navegador ni siquiera aplica CORS.
# ---------------------------------------------------------------------------
# ---------------------------------------------------------------------------
# adjuntos — bucket para los archivos que la gente sube a sus proyectos.
#
# Bucket propio y no el del frontend: cmd_front hace `s3 sync --delete`, así que los adjuntos
# desaparecerían en cada publicación del frontend.
# ---------------------------------------------------------------------------
cmd_adjuntos() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  local bucket="$PROYECTO-adjuntos-$(cuenta)"
  local cuenta_id; cuenta_id=$(cuenta)

  azul "1/3 · Bucket"
  if ! aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
    aws s3api create-bucket --bucket "$bucket" >/dev/null
  fi
  aws s3api delete-public-access-block --bucket "$bucket" >/dev/null 2>&1 || true
  ok "$bucket"

  azul "2/3 · Política"
  # Dos sentencias. La primera hace públicos los archivos, que es lo decidido: un proyecto
  # público muestra sus adjuntos sin pedir sesión. La segunda le da permiso a LabRole, que es
  # quien firma las subidas: se concede desde el recurso porque en el laboratorio no se pueden
  # leer ni editar las políticas de identidad.
  aws s3api put-bucket-policy --bucket "$bucket" --policy "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [
      {
        \"Sid\": \"LecturaPublicaDeAdjuntos\",
        \"Effect\": \"Allow\", \"Principal\": \"*\",
        \"Action\": \"s3:GetObject\",
        \"Resource\": \"arn:aws:s3:::$bucket/adjuntos/*\"
      },
      {
        \"Sid\": \"LabRoleAdministraAdjuntos\",
        \"Effect\": \"Allow\",
        \"Principal\": { \"AWS\": \"arn:aws:iam::$cuenta_id:role/LabRole\" },
        \"Action\": [\"s3:PutObject\", \"s3:GetObject\", \"s3:DeleteObject\"],
        \"Resource\": \"arn:aws:s3:::$bucket/adjuntos/*\"
      }
    ]
  }" >/dev/null 2>&1 || { falla "El laboratorio no permite esta política de bucket"; exit 1; }
  ok "lectura pública en adjuntos/ y escritura para LabRole"

  azul "3/3 · CORS"
  # El navegador sube directo a S3, así que el bucket tiene que aceptar PUT desde el origen de
  # la aplicación. ETag se expone porque es lo que devuelve S3 al terminar la subida.
  aws s3api put-bucket-cors --bucket "$bucket" --cors-configuration "{
    \"CORSRules\": [{
      \"AllowedOrigins\": [\"https://$API_ID.execute-api.$REGION.amazonaws.com\", \"http://localhost:5173\"],
      \"AllowedMethods\": [\"PUT\", \"GET\", \"HEAD\"],
      \"AllowedHeaders\": [\"*\"],
      \"ExposeHeaders\": [\"ETag\"],
      \"MaxAgeSeconds\": 3600
    }]
  }" >/dev/null
  ok "PUT y GET desde la aplicación"

  guardar_recurso BUCKET_ADJUNTOS "$bucket"
  azul "\nBucket de adjuntos listo: $bucket"
}

cmd_front() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  # Las VITE_* de Azure viven en frontend-web/.env.local, no en el .env de la raíz: se cargan aquí
  # para no tener que exportarlas a mano. Lo que ya venga del entorno tiene prioridad.
  local local_front="$RAIZ/frontend-web/.env.local"
  if [[ -f "$local_front" ]]; then
    while IFS='=' read -r clave valor; do
      [[ "$clave" =~ ^VITE_(AZURE_CLIENT_ID|AZURE_SCOPE)$ && -z "${!clave:-}" ]] && export "$clave=$valor" || true
    done < <(grep -E '^VITE_' "$local_front")
  fi
  # Sin estos valores el front se compila igual pero el login falla, y el error recién
  # aparece en el navegador. Mejor fallar aquí.
  [[ -z "${VITE_AZURE_CLIENT_ID:-}" || -z "${VITE_AZURE_SCOPE:-}" || -z "${AZURE_TENANT_ID:-}" ]] && {
    falla "Faltan VITE_AZURE_CLIENT_ID o VITE_AZURE_SCOPE (frontend-web/.env.local), o AZURE_TENANT_ID (.env)"
    exit 1
  }
  datos_cognito

  local bucket="$PROYECTO-web-$(cuenta)"

  azul "1/4 · Bucket de S3"
  if ! aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
    aws s3api create-bucket --bucket "$bucket" >/dev/null
  fi
  # El sitio necesita lectura pública. El contenido es el frontend compilado: no hay nada
  # sensible ahí, los identificadores de Azure y de Cognito son públicos por diseño del flujo.
  aws s3api delete-public-access-block --bucket "$bucket" >/dev/null 2>&1 || true
  aws s3api put-bucket-policy --bucket "$bucket" --policy "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [{
      \"Effect\": \"Allow\", \"Principal\": \"*\",
      \"Action\": \"s3:GetObject\",
      \"Resource\": \"arn:aws:s3:::$bucket/*\"
    }]
  }" >/dev/null 2>&1 || { falla "El laboratorio no permite buckets públicos"; exit 1; }
  # index.html también como página de error: la aplicación es de una sola página.
  aws s3 website "s3://$bucket" --index-document index.html --error-document index.html

  # CORS también en el bucket: la ruta por defecto del API es implícita y no se puede borrar,
  # así que el OPTIONS del preflight termina en S3 y sin esto responde 403.
  aws s3api put-bucket-cors --bucket "$bucket" --cors-configuration '{
    "CORSRules": [{
      "AllowedOrigins": ["http://localhost:5173"],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedHeaders": ["Authorization", "Content-Type"],
      "MaxAgeSeconds": 3600
    }]
  }' >/dev/null
  ok "$bucket (sitio estático, con CORS)"

  azul "2/4 · Construyendo el frontend"
  # VITE_BFF_URL vacío = llamadas relativas al mismo origen, que es el API Gateway.
  ( cd "$RAIZ/frontend-web" && npm install --silent --no-audit --no-fund \
    && VITE_AZURE_CLIENT_ID="${VITE_AZURE_CLIENT_ID:-}" \
       VITE_AZURE_TENANT_ID="${AZURE_TENANT_ID:-}" \
       VITE_AZURE_SCOPE="${VITE_AZURE_SCOPE:-}" \
       VITE_COGNITO_AUTORIDAD="$COGNITO_ISSUER_URI" \
       VITE_COGNITO_CLIENT_ID="$COGNITO_AUDIENCIA" \
       VITE_COGNITO_DOMINIO="$COGNITO_DOMINIO" \
       VITE_BFF_URL="" npm run build --silent )
  ok "compilado"

  azul "3/4 · Subiendo a S3"
  aws s3 sync "$RAIZ/frontend-web/dist" "s3://$bucket" --delete --only-show-errors

  # Caché: los archivos de assets llevan un hash en el nombre, así que cambian de nombre cuando
  # cambia su contenido y se pueden cachear para siempre. El index.html NO: si el navegador se
  # lo queda, sigue pidiendo los archivos del despliegue anterior aunque ya no existan.
  aws s3 cp "s3://$bucket/index.html" "s3://$bucket/index.html" --metadata-directive REPLACE \
    --content-type "text/html; charset=utf-8" --cache-control "no-cache, must-revalidate" \
    --only-show-errors
  # JS y CSS se suben comprimidos con gzip: ni S3 ni el API Gateway comprimen, y el JS pesa más
  # de 600 KB sin comprimir (unos 170 KB con gzip). Todo navegador acepta gzip.
  # El tipo de contenido va explícito: sin él quedan como binary/octet-stream y el navegador los
  # rechaza, con la página en blanco de síntoma.
  local archivo tipo comprimido; comprimido=$(mktemp)
  for archivo in "$RAIZ"/frontend-web/dist/assets/*.js "$RAIZ"/frontend-web/dist/assets/*.css; do
    [[ -f "$archivo" ]] || continue
    case "$archivo" in *.js) tipo="text/javascript; charset=utf-8" ;; *) tipo="text/css; charset=utf-8" ;; esac
    gzip -9 -n -c "$archivo" > "$comprimido"
    aws s3 cp "$comprimido" "s3://$bucket/assets/$(basename "$archivo")" --content-encoding gzip \
      --content-type "$tipo" --cache-control "public, max-age=31536000, immutable" --only-show-errors
  done
  rm -f "$comprimido"
  ok "subido: JS y CSS con gzip, tipos de contenido y caché correctos"

  azul "4/4 · Configurando el API Gateway"
  cmd_apigw

  # La URL del sitio se arma acá y no se toma de cmd_apigw: allá es una variable local
  # y desde esta función no se ve, así que el script moría al final con "unbound variable"
  # justo después de haber hecho bien todo el trabajo.
  guardar_recurso SITIO_WEB "http://$bucket.s3-website-$REGION.amazonaws.com"
  cmd_urls
}

# ---------------------------------------------------------------------------
# apigw — el API Manager como intermediario real, no como simple proxy. Hace tres cosas:
#
#   1. Una ruta por microservicio Y por método, no un comodín.
#   2. Un validador de JWT propio, que comprueba emisor y audiencia ANTES del backend.
#   3. CORS con orígenes concretos y sin comodines.
#
# El frontend y la ruta de salud quedan abiertos: si el validador cubriera la ruta por
# defecto, el navegador no podría ni descargar la aplicación.
# ---------------------------------------------------------------------------
cmd_apigw() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  exigir_azure
  datos_cognito

  local dns bucket sitio
  dns=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
    --query 'LoadBalancers[0].DNSName' --output text)
  bucket="$PROYECTO-web-$(cuenta)"
  sitio="http://$bucket.s3-website-$REGION.amazonaws.com"

  # El AWS CLI aplica --query página por página, así que con muchas rutas devuelve un
  # "None" por cada página que no coincide y el identificador se vuelve inservible.
  # Se pide el JSON completo, que sí viene unificado, y se filtra con jq.
  id_ruta() {
    aws apigatewayv2 get-routes --api-id "$API_ID" --output json \
      | jq -r --arg k "$1" '.Items[] | select(.RouteKey==$k) | .RouteId' | head -1
  }
  id_integracion() {
    aws apigatewayv2 get-integrations --api-id "$API_ID" --output json \
      | jq -r --arg u "$1" '.Items[] | select(.IntegrationUri==$u) | .IntegrationId' | head -1
  }

  # --- Validador de JWT: autorizador Lambda con los dos emisores -------------
  # Un autorizador JWT nativo acepta un solo emisor, y la plataforma tiene dos: Entra ID (login
  # con Microsoft, access token) y Cognito (login con Google, ID token). La función valida
  # firma, vigencia, emisor y audiencia de cada uno (infra/autorizador/index.mjs).
  # Diferencia con el autorizador JWT: sin token responde 401, pero un token inválido, 403.
  local acc fn_nombre fn_arn emisores zip auth_id
  acc=$(cuenta)
  fn_nombre="$PROYECTO-autorizador"
  emisores=$(jq -nc --arg ai "https://login.microsoftonline.com/$AZURE_TENANT_ID/v2.0" --arg aa "$AZURE_CLIENT_ID" \
    --arg ci "$COGNITO_ISSUER_URI" --arg ca "$COGNITO_AUDIENCIA" \
    '[{iss:$ai, aud:$aa}] + (if $ci != "" then [{iss:$ci, aud:$ca}] else [] end)')
  zip=$(mktemp -d)/autorizador.zip
  ( cd "$RAIZ/infra/autorizador" && zip -q "$zip" index.mjs )
  local entorno; entorno=$(jq -nc --arg e "$emisores" '{Variables:{EMISORES:$e}}')
  if aws lambda get-function --function-name "$fn_nombre" >/dev/null 2>&1; then
    aws lambda update-function-code --function-name "$fn_nombre" --zip-file "fileb://$zip" >/dev/null
    aws lambda wait function-updated --function-name "$fn_nombre"
    aws lambda update-function-configuration --function-name "$fn_nombre" --environment "$entorno" \
      --memory-size 512 >/dev/null
    aws lambda wait function-updated --function-name "$fn_nombre"
  else
    aws lambda create-function --function-name "$fn_nombre" --runtime nodejs20.x --handler index.handler \
      --role "arn:aws:iam::$acc:role/LabRole" --zip-file "fileb://$zip" --timeout 10 --memory-size 512 \
      --environment "$entorno" >/dev/null \
      || { falla "El laboratorio no permitió crear la Lambda (ver plan B en docs/guia-cognito-google.md)"; exit 1; }
    aws lambda wait function-active-v2 --function-name "$fn_nombre"
  fi
  rm -rf "$(dirname "$zip")"
  fn_arn=$(aws lambda get-function --function-name "$fn_nombre" --query 'Configuration.FunctionArn' --output text)
  aws lambda add-permission --function-name "$fn_nombre" --statement-id apigw-invoca \
    --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
    --source-arn "arn:aws:execute-api:$REGION:$acc:$API_ID/*" >/dev/null 2>&1 || true
  ok "Lambda $fn_nombre: $(echo "$emisores" | jq length) emisor(es)"

  local auth_lambda="$PROYECTO-dos-emisores"
  auth_id=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" --output json \
    | jq -r --arg n "$auth_lambda" '.Items[] | select(.Name==$n) | .AuthorizerId' | head -1)
  local uri="arn:aws:apigateway:$REGION:lambda:path/2015-03-31/functions/$fn_arn/invocations"
  if [[ -z "$auth_id" ]]; then
    auth_id=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name "$auth_lambda" \
      --authorizer-type REQUEST --authorizer-uri "$uri" \
      --authorizer-payload-format-version 2.0 --enable-simple-responses \
      --identity-source '$request.header.Authorization' --authorizer-result-ttl-in-seconds 3600 \
      --query AuthorizerId --output text)
  else
    aws apigatewayv2 update-authorizer --api-id "$API_ID" --authorizer-id "$auth_id" \
      --authorizer-uri "$uri" --authorizer-result-ttl-in-seconds 3600 >/dev/null
  fi
  # Caché de una hora, lo que dura un token: la Lambda corre una vez por token y no cada 5 minutos,
  # y cada arranque en frío le sumaba más de un segundo a una petición. El backend igual valida la
  # vigencia en cada llamada, así que un token vencido sigue recibiendo 401.
  ok "autorizador '$auth_lambda' (Lambda): Entra ID + Cognito"

  # --- Rutas ----------------------------------------------------------------
  # La integración de un destino, creándola si falta. Una vez por servicio y no por método:
  # si no, son veinte llamadas a la API por corrida.
  # $1 = destino · $2 = descripción
  integracion() {
    local id
    id=$(id_integracion "$1")
    if [[ -z "$id" ]]; then
      aws apigatewayv2 create-integration --api-id "$API_ID" \
        --integration-type HTTP_PROXY --integration-method ANY \
        --integration-uri "$1" --payload-format-version 1.0 \
        --description "$2" --query IntegrationId --output text
    else
      # La descripción se refresca siempre: en la consola de AWS es lo único legible de
      # una integración, y sin ella solo se ve un identificador de siete caracteres.
      aws apigatewayv2 update-integration --api-id "$API_ID" --integration-id "$id" \
        --description "$2" >/dev/null
      echo "$id"
    fi
  }

  # $1 = clave de ruta · $2 = id de integración · $3 = "jwt" para exigir token
  ruta() {
    local id extra
    extra=(--authorization-type NONE)
    [[ "${3:-}" == "jwt" ]] && extra=(--authorization-type CUSTOM --authorizer-id "$auth_id")

    id=$(id_ruta "$1")
    if [[ -n "$id" ]]; then
      aws apigatewayv2 update-route --api-id "$API_ID" --route-id "$id" \
        --target "integrations/$2" "${extra[@]}" >/dev/null
    else
      aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$1" \
        --target "integrations/$2" "${extra[@]}" >/dev/null
    fi
  }

  # Primero se borran las rutas comodín de corridas anteriores: si quedaran, convivirían
  # con las nuevas y además volverían a capturar el preflight de CORS.
  local borradas=0 rid
  for clave in 'ANY /api/{proxy+}' 'ANY /api/v1/bff/{proxy+}' 'ANY /api/v1/usuarios/{proxy+}' \
               'ANY /api/v1/proyectos/{proxy+}' 'ANY /api/v1/colaboraciones/{proxy+}' \
               'ANY /api/v1/notificaciones/{proxy+}' 'ANY /api/v1/mensajeria/{proxy+}'; do
    rid=$(id_ruta "$clave")
    [[ -n "$rid" ]] && aws apigatewayv2 delete-route --api-id "$API_ID" --route-id "$rid" \
      && borradas=$((borradas + 1))
  done
  [[ $borradas -gt 0 ]] && aviso "$borradas rutas comodín anteriores eliminadas"

  # Métodos reales en vez de ANY: ANY también captura el OPTIONS del preflight, que viaja sin
  # token. El validador lo rechazaría y el navegador lo reportaría como bloqueo de CORS.
  local servicios_api=(
    "bff|bff-web - respuestas agregadas para el frontend"
    "usuarios|ms-usuarios - perfiles, roles y visibilidad"
    "proyectos|ms-proyectos - vitrina de proyectos"
    "colaboraciones|ms-contacto - solicitudes de colaboracion con consentimiento"
    "notificaciones|ms-notificaciones - avisos generados por los eventos"
    "mensajeria|ms-mensajeria - administracion de colas, topicos y suscripciones"
  )
  local integ integ_raiz
  for entrada in "${servicios_api[@]}"; do
    local path="${entrada%%|*}" desc="${entrada##*|}"

    # Dos rutas por servicio: la colección en sí y todo lo que cuelga de ella.
    # {proxy+} exige al menos un segmento más, así que sin la primera un
    # GET /api/v1/proyectos (listar) o un POST (publicar) no encontrarían ruta.
    integ_raiz=$(integracion "http://$dns/api/v1/$path" "$desc - coleccion")
    for m in GET POST; do
      ruta "$m /api/v1/$path" "$integ_raiz" jwt
    done

    integ=$(integracion "http://$dns/api/v1/$path/{proxy}" "$desc")
    for m in GET POST PUT PATCH DELETE; do
      ruta "$m /api/v1/$path/{proxy+}" "$integ" jwt
    done
    printf '     %-26s %s\n' "/api/v1/$path" "GET POST · requieren JWT"
    printf '     %-26s %s\n' "/api/v1/$path/*" "GET POST PUT PATCH DELETE · requieren JWT"
  done

  integ=$(integracion "http://$dns/actuator/{proxy}" "Salud de los servicios - sin token, para monitoreo")
  ruta 'ANY /actuator/{proxy+}' "$integ"
  printf '     %-26s %s\n' "/actuator/*" "abierta, para monitoreo"

  # Rutas GET explícitas y no la ruta por defecto: así el OPTIONS del preflight no coincide
  # con nada y lo contesta el propio API Gateway en vez de terminar en S3.
  # DOS integraciones: la raíz al bucket pelado, y el resto arrastrando el path con {proxy}.
  # Sin eso, /assets/index-XXX.js devolvía el index.html y la aplicación no arrancaba.
  # La raíz lleva la barra final: sin ella, la vuelta de Cognito (/?code=…) llegaba a S3 con la
  # ruta vacía y S3 respondía 302 a su propio dominio http, donde MSAL no tiene crypto.
  integ=$(integracion "$sitio/" "Frontend estatico en S3 - pagina de entrada")
  ruta 'GET /' "$integ"

  local integ_assets
  integ_assets=$(integracion "$sitio/{proxy}" "Frontend estatico en S3 - archivos de la aplicacion React")
  ruta 'GET /{proxy+}' "$integ_assets"
  printf '     %-26s %s\n' "GET /" "frontend en S3, abierto"
  printf '     %-26s %s\n' "GET /*" "archivos del frontend, abierto"

  # La ruta por defecto no se borra: es implícita y AWS la recrea. Queda apuntando al frontend,
  # y por eso el bucket lleva su propio CORS (ver cmd_front).
  printf '     %-26s %s\n' "por defecto" "frontend en S3 (implícita, no se puede quitar)"

  # --- Limpieza -------------------------------------------------------------
  # Cada corrida anterior pudo dejar integraciones que ya no usa ninguna ruta.
  # Sin esto la consola se llena de entradas sin descripción y cuesta leerla.
  local usadas huerfanas=0
  usadas=$(aws apigatewayv2 get-routes --api-id "$API_ID" --output json \
    | jq -r '.Items[].Target' | sed 's|integrations/||')
  for i in $(aws apigatewayv2 get-integrations --api-id "$API_ID" --output json \
               | jq -r '.Items[].IntegrationId'); do
    if ! echo "$usadas" | grep -qw "$i"; then
      aws apigatewayv2 delete-integration --api-id "$API_ID" --integration-id "$i" 2>/dev/null \
        && huerfanas=$((huerfanas + 1))
    fi
  done
  [[ $huerfanas -gt 0 ]] && ok "$huerfanas integraciones sin uso eliminadas"

  # El autorizador JWT de un solo emisor (EP1) ya no lo usa ninguna ruta: se elimina.
  local auth_viejo
  auth_viejo=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" --output json \
    | jq -r --arg n "$AUTH_NOMBRE" '.Items[] | select(.Name==$n) | .AuthorizerId' | head -1)
  [[ -n "$auth_viejo" ]] && aws apigatewayv2 delete-authorizer --api-id "$API_ID" --authorizer-id "$auth_viejo" \
    >/dev/null 2>&1 && ok "autorizador JWT anterior ('$AUTH_NOMBRE') eliminado"

  # --- Registro de accesos ---------------------------------------------------
  # Sin esto, un rechazo del API Gateway no deja rastro: no se sabe qué ruta coincidió, si
  # falló el validador o si el error vino del backend. Diagnosticar un 403 se vuelve adivinar.
  local grupo_log="/aws/apigateway/$PROYECTO"
  aws logs create-log-group --log-group-name "$grupo_log" >/dev/null 2>&1 || true
  local formato='{"id":"$context.requestId","hora":"$context.requestTime","metodo":"$context.httpMethod","ruta":"$context.path","rutaCoincidente":"$context.routeKey","estado":"$context.status","integracion":"$context.integration.status","errorIntegracion":"$context.integration.error","errorAutorizador":"$context.authorizer.error","mensaje":"$context.error.message"}'
  local destino="arn:aws:logs:$REGION:$(cuenta):log-group:$grupo_log"
  aws apigatewayv2 update-stage --api-id "$API_ID" --stage-name '$default' \
    --access-log-settings "$(printf '{"DestinationArn":"%s","Format":%s}' "$destino" "$(printf '%s' "$formato" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')")" \
    >/dev/null 2>&1 && ok "registro de accesos en $grupo_log" || aviso "no se pudo activar el registro de accesos"

  # --- CORS -----------------------------------------------------------------
  # Orígenes concretos, sin comodines. El preflight lo responde el API Gateway sin pasar por
  # el validador, porque el navegador no manda el token en esa petición.
  aws apigatewayv2 update-api --api-id "$API_ID" --cors-configuration "{
    \"AllowOrigins\": [\"http://localhost:5173\", \"https://$API_ID.execute-api.$REGION.amazonaws.com\"],
    \"AllowMethods\": [\"GET\",\"POST\",\"PUT\",\"PATCH\",\"DELETE\",\"OPTIONS\"],
    \"AllowHeaders\": [\"Authorization\",\"Content-Type\"],
    \"MaxAge\": 3600
  }" >/dev/null
  ok "CORS con orígenes declarados, sin comodines"
}

# ---------------------------------------------------------------------------
# iniciar / apagar — control de costo entre jornadas.
# ---------------------------------------------------------------------------
cmd_iniciar() {
  # La base va primero: si las tareas arrancan antes, no encuentran a dónde conectarse.
  local estado
  estado=$(aws rds describe-db-instances --db-instance-identifier "$PROYECTO-db" \
    --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null)
  if [[ "$estado" == "stopped" ]]; then
    aws rds start-db-instance --db-instance-identifier "$PROYECTO-db" >/dev/null
    azul "Encendiendo la base (tarda ~5 min)..."
    aws rds wait db-instance-available --db-instance-identifier "$PROYECTO-db"
  fi
  ok "base disponible"

  local rabbit; rabbit=$(id_ec2_rabbit)
  if [[ -n "$rabbit" ]]; then
    aws ec2 start-instances --instance-ids "$rabbit" >/dev/null 2>&1 || true
    aws ec2 wait instance-running --instance-ids "$rabbit" && ok "RabbitMQ (EC2) encendido"
  fi

  local servicio
  for servicio in $(servicios_ecs); do
    aws ecs update-service --cluster "$CLUSTER" --service "$servicio" --desired-count 1 >/dev/null 2>&1 || true
  done
  azul "Levantando las tareas (tardan unos minutos en pasar el health check)..."
  aws ecs wait services-stable --cluster "$CLUSTER" --services $(servicios_ecs) && ok "tareas estables"
  cmd_urls
}

cmd_apagar() {
  local servicio
  for servicio in $(servicios_ecs); do
    aws ecs update-service --cluster "$CLUSTER" --service "$servicio" --desired-count 0 >/dev/null 2>&1 || true
  done
  ok "tareas de Fargate en cero"

  local rabbit; rabbit=$(id_ec2_rabbit)
  [[ -n "$rabbit" ]] && aws ec2 stop-instances --instance-ids "$rabbit" >/dev/null 2>&1 && ok "RabbitMQ (EC2) detenido"

  # Detener la base conserva los datos y el endpoint; solo se sigue pagando el disco.
  # Ojo: AWS la vuelve a encender sola a los 7 días.
  aws rds stop-db-instance --db-instance-identifier "$PROYECTO-db" >/dev/null 2>&1 \
    && ok "base detenida (los datos se conservan)" \
    || aviso "la base ya estaba detenida o está cambiando de estado"

  aviso "El ALB sigue cobrando (~\$0,55 por día). Se borra con:"
  echo "      aws elbv2 delete-load-balancer --load-balancer-arn \$ALB_ARN"
}

cmd_urls() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || return 0
  azul "\nURLs"
  echo "  Aplicación:      https://$API_ID.execute-api.$REGION.amazonaws.com   ← esta es la que se comparte"
  echo "  ALB (directo):   http://$ALB_DNS"
  echo "  API Gateway:     https://$API_ID.execute-api.$REGION.amazonaws.com"
  echo "  Health:          http://$ALB_DNS/actuator/health"
}

# ---------------------------------------------------------------------------
# mensajeria — tópico SNS, colas SQS y DLQ de la EP2. Idempotente y nunca borra.
#
# Es la misma topología que declara ms-notificaciones al arrancar; tenerla aquí permite crearla
# antes del primer despliegue y rehacerla en otra cuenta. Correrla sobre lo que ya existe no
# cambia nada: create-queue y create-topic devuelven el recurso existente.
# ---------------------------------------------------------------------------
cmd_mensajeria() {
  command -v jq >/dev/null || { falla "Falta jq (brew install jq)"; exit 1; }
  local topico
  topico=$(aws sns create-topic --name "$PROYECTO-eventos" --query TopicArn --output text)
  ok "tópico $PROYECTO-eventos"

  url() { aws sqs get-queue-url --queue-name "$1" --query QueueUrl --output text; }
  arn() { aws sqs get-queue-attributes --queue-url "$(url "$1")" --attribute-names QueueArn --query Attributes.QueueArn --output text; }

  # Deja que SOLO este tópico escriba en la cola $1. Sin esto SNS descarta en silencio.
  permiso() {
    local politica
    politica=$(jq -nc --arg cola "$(arn "$1")" --arg topico "$topico" '{Version:"2012-10-17",
      Statement:[{Effect:"Allow", Principal:{Service:"sns.amazonaws.com"}, Action:"sqs:SendMessage",
                  Resource:$cola, Condition:{ArnEquals:{"aws:SourceArn":$topico}}}]}')
    aws sqs set-queue-attributes --queue-url "$(url "$1")" \
      --attributes "$(jq -nc --arg p "$politica" '{Policy:$p}')"
  }

  # Si la cola ya tiene el permiso (por ejemplo, porque se suscribió desde la consola), no se
  # reescribe su política: set-queue-attributes la reemplaza entera.
  permiso_si_falta() {
    local actual
    actual=$(aws sqs get-queue-attributes --queue-url "$(url "$1")" --attribute-names Policy \
      --query Attributes.Policy --output text 2>/dev/null || true)
    [[ "$actual" == *"$topico"* ]] && return 0
    permiso "$1"
  }

  # create-queue falla si la cola existe con atributos que difieren aunque sea en forma (por
  # ejemplo, colas creadas desde la consola): por eso primero se pregunta si existe.
  crear_cola() {
    if aws sqs get-queue-url --queue-name "$1" >/dev/null 2>&1; then
      echo "    · $1 ya existe: no se modifica"
    else
      aws sqs create-queue --queue-name "$1" --attributes "$2" >/dev/null
    fi
  }
  local descarte='{"MessageRetentionPeriod":"1209600","SqsManagedSseEnabled":"true"}'

  crear_cola "$PROYECTO-eventos-no-entregados" "$descarte"
  permiso_si_falta "$PROYECTO-eventos-no-entregados"
  ok "DLQ de la suscripción: $PROYECTO-eventos-no-entregados"

  local cola sub filtro
  for cola in "$PROYECTO-notificaciones-proyectos" "$PROYECTO-notificaciones-colaboraciones" "$PROYECTO-auditoria-eventos"; do
    crear_cola "$cola-descartes" "$descarte"
    crear_cola "$cola" "$(jq -nc --arg dlq "$(arn "$cola-descartes")" \
      '{VisibilityTimeout:"30", ReceiveMessageWaitTimeSeconds:"20", SqsManagedSseEnabled:"true",
        RedrivePolicy:({deadLetterTargetArn:$dlq, maxReceiveCount:"3"}|tojson)}')"
    permiso_si_falta "$cola"

    sub=$(aws sns subscribe --topic-arn "$topico" --protocol sqs --notification-endpoint "$(arn "$cola")" \
      --attributes "$(jq -nc --arg dlq "$(arn "$PROYECTO-eventos-no-entregados")" \
        '{RawMessageDelivery:"true", RedrivePolicy:({deadLetterTargetArn:$dlq}|tojson)}')" \
      --return-subscription-arn --query SubscriptionArn --output text 2>/dev/null \
      || aws sns list-subscriptions-by-topic --topic-arn "$topico" \
           --query "Subscriptions[?Endpoint=='$(arn "$cola")'].SubscriptionArn | [0]" --output text)

    case "$cola" in
      *proyectos)      filtro='{"tipo":[{"prefix":"proyecto."}]}' ;;
      *colaboraciones) filtro='{"tipo":[{"prefix":"colaboracion."}]}' ;;
      *)               filtro='' ;;   # auditoría: sin filtro, recibe todo
    esac
    [[ -n "$filtro" ]] && aws sns set-subscription-attributes --subscription-arn "$sub" \
      --attribute-name FilterPolicy --attribute-value "$filtro"
    ok "$cola → $cola-descartes · filtro ${filtro:-(ninguno, recibe todo)}"
  done

  guardar_recurso TOPICO_EVENTOS_ARN "$topico"
  azul "\nMensajería lista. El ARN del tópico quedó en infra/.recursos (ignorado por git)."
}

# ---------------------------------------------------------------------------
# cognito — el login con Google: user pool, dominio, app client y Google como IdP. Idempotente.
# (El login con Microsoft sigue en Azure AD / Entra ID; esto no lo toca.)
#
# Sin registro abierto: las cuentas de Google se crean solas al primer login, pero el backend
# solo les da rol si están en CORREOS_PERMITIDOS.
#
# Google es opcional: si GOOGLE_CLIENT_ID está en el entorno, el secreto se pide por teclado y
# no se guarda en ningún archivo.
# ---------------------------------------------------------------------------
cmd_cognito() {
  local acc pool dominio api_url cliente proveedores
  acc=$(cuenta)
  dominio="$PROYECTO-$acc"
  api_url=$(aws apigatewayv2 get-apis --query "Items[?Name=='$API_NOMBRE'].ApiEndpoint" --output text)

  azul "1/4 · User pool"
  pool=$(aws cognito-idp list-user-pools --max-results 60 --output json \
    | jq -r --arg n "$POOL_NOMBRE" '.UserPools[] | select(.Name==$n) | .Id' | head -1)
  if [[ -z "$pool" ]]; then
    pool=$(aws cognito-idp create-user-pool --pool-name "$POOL_NOMBRE" \
      --username-attributes email --auto-verified-attributes email \
      --admin-create-user-config AllowAdminCreateUserOnly=true \
      --policies 'PasswordPolicy={MinimumLength=10,RequireUppercase=true,RequireLowercase=true,RequireNumbers=true,RequireSymbols=false}' \
      --account-recovery-setting 'RecoveryMechanisms=[{Priority=1,Name=verified_email}]' \
      --query 'UserPool.Id' --output text)
  fi
  ok "$POOL_NOMBRE ($pool)"

  azul "2/4 · Dominio de inicio de sesión"
  if ! aws cognito-idp describe-user-pool-domain --domain "$dominio" --query 'DomainDescription.UserPoolId' \
       --output text 2>/dev/null | grep -q "$pool"; then
    # Versión 1 = la página de login clásica: no necesita configurar una marca para funcionar.
    aws cognito-idp create-user-pool-domain --domain "$dominio" --user-pool-id "$pool" \
      --managed-login-version 1 >/dev/null 2>&1 \
      || aws cognito-idp create-user-pool-domain --domain "$dominio" --user-pool-id "$pool" >/dev/null
  fi
  ok "https://$dominio.auth.$REGION.amazoncognito.com"

  azul "3/4 · Google como proveedor externo"
  # El login con correo y contraseña es de Microsoft (Entra ID): aquí solo entra Google. Mientras
  # Google no esté configurado, el app client necesita algún proveedor y queda con COGNITO.
  proveedores="COGNITO"
  if [[ -n "${GOOGLE_CLIENT_ID:-}" ]]; then
    local secreto="${GOOGLE_CLIENT_SECRET:-}"
    if [[ -z "$secreto" ]]; then
      read -r -s -p "  Client secret de Google (no se muestra ni se guarda): " secreto; echo
    fi
    local detalles="client_id=$GOOGLE_CLIENT_ID,client_secret=$secreto,authorize_scopes=openid email profile"
    local mapeo="email=email,email_verified=email_verified,name=name,username=sub"
    if aws cognito-idp describe-identity-provider --user-pool-id "$pool" --provider-name Google >/dev/null 2>&1; then
      aws cognito-idp update-identity-provider --user-pool-id "$pool" --provider-name Google \
        --provider-details "$detalles" --attribute-mapping "$mapeo" >/dev/null
    else
      aws cognito-idp create-identity-provider --user-pool-id "$pool" --provider-name Google \
        --provider-type Google --provider-details "$detalles" --attribute-mapping "$mapeo" >/dev/null
    fi
    unset secreto detalles
    proveedores="Google"
    ok "Google configurado"
  elif aws cognito-idp describe-identity-provider --user-pool-id "$pool" --provider-name Google >/dev/null 2>&1; then
    proveedores="Google"
    ok "Google ya estaba configurado"
  else
    aviso "Sin Google: define GOOGLE_CLIENT_ID y vuelve a correr el comando (ver docs/guia-cognito-google.md)"
  fi

  azul "4/4 · App client del frontend (público, sin secreto, Authorization Code + PKCE)"
  # Las URL tienen que coincidir EXACTO con window.location.origin: sin barra final.
  local urls="$api_url http://localhost:5173"
  local args=(--user-pool-id "$pool" --client-name "$CLIENTE_NOMBRE"
    --allowed-o-auth-flows code --allowed-o-auth-flows-user-pool-client
    --allowed-o-auth-scopes openid email profile
    --callback-urls $urls --logout-urls $urls
    --supported-identity-providers $proveedores
    --explicit-auth-flows ALLOW_REFRESH_TOKEN_AUTH ALLOW_USER_SRP_AUTH
    --prevent-user-existence-errors ENABLED
    --id-token-validity 60 --access-token-validity 60 --refresh-token-validity 1
    --token-validity-units AccessToken=minutes,IdToken=minutes,RefreshToken=days)
  cliente=$(aws cognito-idp list-user-pool-clients --user-pool-id "$pool" --output json \
    | jq -r --arg n "$CLIENTE_NOMBRE" '.UserPoolClients[] | select(.ClientName==$n) | .ClientId' | head -1)
  if [[ -z "$cliente" ]]; then
    cliente=$(aws cognito-idp create-user-pool-client "${args[@]}" --no-generate-secret \
      --query 'UserPoolClient.ClientId' --output text)
  else
    # update reemplaza la configuración completa: por eso se mandan todos los parámetros.
    aws cognito-idp update-user-pool-client "${args[@]:2}" --user-pool-id "$pool" --client-id "$cliente" >/dev/null
  fi
  ok "$CLIENTE_NOMBRE ($cliente) · proveedores: $proveedores"

  guardar_recurso COGNITO_POOL_ID "$pool"
  guardar_recurso COGNITO_CLIENT_ID "$cliente"
  guardar_recurso COGNITO_DOMINIO "$dominio.auth.$REGION.amazoncognito.com"
  azul "\nCognito listo. Para desarrollo local, en .env:"
  echo "  COGNITO_ISSUER_URI=https://cognito-idp.$REGION.amazonaws.com/$pool"
  echo "  COGNITO_AUDIENCIA=$cliente"
  echo "  Y en frontend-web/.env.local: VITE_COGNITO_AUTORIDAD (igual al emisor),"
  echo "  VITE_COGNITO_CLIENT_ID=$cliente y VITE_COGNITO_DOMINIO=$dominio.auth.$REGION.amazoncognito.com"
}

# ---------------------------------------------------------------------------
# rabbitmq — el segundo broker en la nube: una EC2 con un nodo en docker-compose.
# Idempotente. Amazon MQ no está habilitado en el lab, por eso va en EC2.
#
#   - Credenciales en Secrets Manager ($RABBIT_SECRETO): nunca en el repo.
#   - 5672 (AMQP) solo para las tareas de Fargate; 15672 (consola) solo para tu IP.
#   - Se entra por SSM (LabInstanceProfile), sin llaves SSH.
# ---------------------------------------------------------------------------
cmd_rabbitmq() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  local vpc sg mi_ip ami id secreto subnet

  azul "1/4 · Credenciales en Secrets Manager"
  if ! aws secretsmanager describe-secret --secret-id "$RABBIT_SECRETO" >/dev/null 2>&1; then
    secreto=$(jq -nc --arg c "$(openssl rand -hex 16)" '{usuario:"duocconecta", clave:$c}')
    aws secretsmanager create-secret --name "$RABBIT_SECRETO" --secret-string "$secreto" >/dev/null
    unset secreto
  fi
  ok "$RABBIT_SECRETO"

  azul "2/4 · Security group"
  vpc="$VPC"
  sg=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$RABBIT_NOMBRE" "Name=vpc-id,Values=$vpc" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null)
  if [[ "$sg" == "None" || -z "$sg" ]]; then
    sg=$(aws ec2 create-security-group --group-name "$RABBIT_NOMBRE" --description "RabbitMQ de DuocConecta" \
      --vpc-id "$vpc" --query GroupId --output text)
  fi
  aws ec2 authorize-security-group-ingress --group-id "$sg" --protocol tcp --port 5672 --source-group "$SG_TAREAS" >/dev/null 2>&1 || true
  mi_ip=$(curl -s https://checkip.amazonaws.com | tr -d '\n')
  aws ec2 authorize-security-group-ingress --group-id "$sg" --protocol tcp --port 15672 --cidr "$mi_ip/32" >/dev/null 2>&1 || true
  ok "$RABBIT_NOMBRE: AMQP solo desde las tareas · consola solo desde $mi_ip"

  azul "3/4 · EC2 con docker-compose"
  id=$(id_ec2_rabbit)
  if [[ -z "$id" ]]; then
    ami=$(aws ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
      --query Parameter.Value --output text)
    # Una subred en una zona que ofrezca t3.small: no todas lo tienen (us-east-1e no), y tomar la
    # primera subred hacía fallar el run-instances según la cuenta.
    local zonas
    zonas=" $(aws ec2 describe-instance-type-offerings --location-type availability-zone \
      --filters Name=instance-type,Values=t3.small --query 'InstanceTypeOfferings[].Location' \
      --output text | tr '\t' ' ') "
    subnet=$(aws ec2 describe-subnets --subnet-ids ${SUBNETS//,/ } \
      --query 'Subnets[].[SubnetId,AvailabilityZone]' --output text \
      | while read -r sid zona; do [[ "$zonas" == *" $zona "* ]] && echo "$sid" && break; done)
    [[ -z "$subnet" ]] && { falla "Ninguna subred está en una zona que ofrezca t3.small"; exit 1; }
    local datos; datos=$(mktemp)
    # Los mismos archivos del repositorio: lo que corre en la nube es lo que se prueba en local.
    cat > "$datos" <<EOU
#!/bin/bash
set -eux
dnf install -y docker jq
systemctl enable --now docker
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL "https://github.com/docker/compose/releases/download/$COMPOSE_VERSION/docker-compose-linux-x86_64" \\
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
mkdir -p /opt/rabbitmq && cd /opt/rabbitmq
echo '$(base64 < "$RAIZ/docker/rabbitmq/docker-compose.yml" | tr -d '\n')' | base64 -d > docker-compose.yml
echo '$(base64 < "$RAIZ/docker/rabbitmq/rabbitmq.conf" | tr -d '\n')' | base64 -d > rabbitmq.conf
S=\$(aws secretsmanager get-secret-value --region $REGION --secret-id $RABBIT_SECRETO --query SecretString --output text)
printf 'RABBITMQ_USUARIO=%s\nRABBITMQ_CLAVE=%s\n' \\
  "\$(echo "\$S" | jq -r .usuario)" "\$(echo "\$S" | jq -r .clave)" > .env
chmod 600 .env
docker compose up -d
EOU
    id=$(aws ec2 run-instances --image-id "$ami" --instance-type t3.small --subnet-id "$subnet" \
      --security-group-ids "$sg" --iam-instance-profile Name=LabInstanceProfile \
      --metadata-options HttpTokens=required --associate-public-ip-address \
      --user-data "file://$datos" \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$RABBIT_NOMBRE}]" \
      --query 'Instances[0].InstanceId' --output text)
    rm -f "$datos"
  fi
  aws ec2 start-instances --instance-ids "$id" >/dev/null 2>&1 || true
  aws ec2 wait instance-running --instance-ids "$id"
  ok "EC2 $id encendida (RabbitMQ tarda ~2 min en quedar listo la primera vez)"

  azul "4/4 · Datos"
  local ip_pub ip_priv
  ip_pub=$(aws ec2 describe-instances --instance-ids "$id" --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
  ip_priv=$(aws ec2 describe-instances --instance-ids "$id" --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
  echo "  AMQP (tareas): $ip_priv:5672"
  echo "  Consola:       http://$ip_pub:15672   (usuario y clave en Secrets Manager: $RABBIT_SECRETO)"
  echo "  Entrar:        aws ssm start-session --target $id   →   sudo docker compose -f /opt/rabbitmq/docker-compose.yml ps"
}

case "${1:-}" in
  verificar) cmd_verificar ;;
  crear)     cmd_crear ;;
  build)     cmd_build "${2:-}" ;;
  desplegar) cmd_desplegar ;;
  front)     cmd_front ;;
  adjuntos)  cmd_adjuntos ;;
  apigw)     cmd_apigw ;;
  iniciar)   cmd_iniciar ;;
  apagar)    cmd_apagar ;;
  urls)      cmd_urls ;;
  mensajeria) cmd_mensajeria ;;
  cognito)   cmd_cognito ;;
  rabbitmq)  cmd_rabbitmq ;;
  *) sed -n '2,25p' "$0"; exit 1 ;;
esac
