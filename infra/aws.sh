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
#
# Las credenciales del lab van en ~/.aws/credentials (panel "AWS Details"), nunca en el repo:
# rotan en cada sesión.

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
PROYECTO="duocconecta"
CLUSTER="$PROYECTO"
SERVICIO_ECS="$PROYECTO"
FAMILIA="$PROYECTO"
SERVICIOS=(ms-usuarios bff-web ms-proyectos ms-contacto)

# Los nombres se declaran acá y no se repiten sueltos por el script. Están pensados para que
# quien mire la consola de AWS entienda de una qué papel cumple cada recurso: la evaluación
# pide mostrar "la instancia de API Manager", así que se llama así y no con una sigla.
API_NOMBRE="$PROYECTO-api-manager"
AUTH_NOMBRE="validador-jwt-entra-id"
RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Puerto y ruta de cada servicio. Se resuelven con funciones y no con arreglos asociativos
# porque macOS trae bash 3.2, que no los soporta: en esa versión el script ni siquiera arranca.
puerto_de() {
  case "$1" in
    bff-web)      echo 8080 ;;
    ms-usuarios)  echo 8081 ;;
    ms-proyectos) echo 8082 ;;
    ms-contacto)  echo 8083 ;;
  esac
}

# Path por el que el ALB enruta hacia cada servicio.
ruta_de() {
  case "$1" in
    bff-web)      echo "/api/v1/bff*" ;;
    ms-usuarios)  echo "/api/v1/usuarios*" ;;
    ms-proyectos) echo "/api/v1/proyectos*" ;;
    ms-contacto)  echo "/api/v1/colaboraciones*" ;;
  esac
}

azul()  { printf '\033[1;34m%b\033[0m\n' "$*"; }
ok()    { printf '  \033[32m✓\033[0m %s\n' "$*"; }
falla() { printf '  \033[31m✗\033[0m %s\n' "$*"; }
aviso() { printf '  \033[33m!\033[0m %s\n' "$*"; }

# Carga .env si existe, para tomar los identificadores de Azure sin pedirlos cada vez.
[[ -f "$RAIZ/.env" ]] && set -a && source "$RAIZ/.env" && set +a

cuenta() { aws sts get-caller-identity --query Account --output text; }
registro() { echo "$(cuenta).dkr.ecr.$REGION.amazonaws.com"; }

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

  azul "\nRecordatorio de costo"
  aviso "ALB y RDS no se apagan solos: 'aws.sh apagar' baja las tareas, pero el ALB sigue cobrando."
  aviso "Mirá el crédito consumido en el panel del Learner Lab todos los días."
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
  for s in ms-usuarios ms-proyectos ms-contacto; do
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

  # Se guardan los identificadores para que 'desplegar' e 'iniciar' no los busquen de nuevo.
  cat > "$RAIZ/infra/.recursos" << EOV
VPC=$vpc
SUBNETS=$subnets
SG_TAREAS=$sg_tareas
ALB_ARN=$alb_arn
ALB_DNS=$dns
API_ID=$api_id
EOV
  aviso "Identificadores guardados en infra/.recursos (ignorado por git)"
  cmd_urls
}

# ---------------------------------------------------------------------------
# build — compila la imagen de un servicio y la sube a ECR.
# ---------------------------------------------------------------------------
cmd_build() {
  local s="${1:?Uso: aws.sh build <ms-usuarios|bff-web|ms-proyectos|ms-contacto>}"
  [[ -d "$RAIZ/$s" ]] || { falla "El módulo $s todavía no existe en el repo"; exit 1; }
  local reg; reg=$(registro)

  aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$reg" >/dev/null
  # Se usa buildx y no 'docker build' porque hace falta fijar la arquitectura: Fargate corre
  # x86_64 y los Mac con chip M son ARM. Sin esto la imagen no arranca en la nube.
  # buildx construye y sube en un solo paso.
  docker buildx build --platform linux/amd64 --push \
    -f "$RAIZ/docker/Dockerfile" --build-arg "SERVICIO=$s" \
    -t "$reg/$PROYECTO/$s:latest" "$RAIZ"
  ok "$s publicado en ECR"
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
  [[ -z "${AZURE_TENANT_ID:-}" || -z "${AZURE_CLIENT_ID:-}" ]] && {
    falla "Faltan AZURE_TENANT_ID o AZURE_CLIENT_ID. Copiá .env.example a .env y completalos."
    exit 1
  }

  # El front se sirve desde el mismo dominio del API Gateway. En un POST el navegador manda
  # igual la cabecera Origin, aunque sea mismo origen, y si ese origen no está declarado el BFF
  # responde 403 "Invalid CORS request" antes de mirar el token. Se agrega solo, derivado del
  # API_ID, para que no dependa de que alguien lo escriba a mano en .env.
  local origenes="${CORS_ORIGENES:-http://localhost:5173}"
  if [[ -n "${API_ID:-}" && "$origenes" != *"$API_ID.execute-api.$REGION.amazonaws.com"* ]]; then
    origenes="$origenes,https://$API_ID.execute-api.$REGION.amazonaws.com"
  fi

  tmp=$(mktemp)  # archivo temporal: la task definition renderizada lleva ARNs y no va al repo
  sed -e "s|__ACCOUNT_ID__|$acc|g" -e "s|__REGISTRO__|$reg|g" -e "s|__TAG__|latest|g" \
      -e "s|__REGION__|$REGION|g" -e "s|__RDS_ENDPOINT__|$endpoint|g" \
      -e "s|__SECRETO_DB_ARN__|$secreto|g" \
      -e "s|__AZURE_TENANT_ID__|${AZURE_TENANT_ID:-}|g" \
      -e "s|__AZURE_CLIENT_ID__|${AZURE_CLIENT_ID:-}|g" \
      -e "s|__CORS_ORIGENES__|$origenes|g" \
      -e "s|__LOG_NIVEL__|${LOG_NIVEL:-INFO}|g" \
      -e "s|__BUCKET_ADJUNTOS__|${BUCKET_ADJUNTOS:-}|g" \
      "$RAIZ/infra/task-definition.json" > "$tmp"

  # Se quita el bloque de comentarios (ECS rechaza campos que no conoce) y, si la imagen de
  # algún servicio todavía no está en ECR, se quita ese contenedor para no bloquear el despliegue:
  # el contenedor es essential, así que sin imagen la tarea entera no arrancaría.
  local filtro='del(._comentario)'
  for s in "${SERVICIOS[@]}"; do
    if ! aws ecr describe-images --repository-name "$PROYECTO/$s" --image-ids imageTag=latest >/dev/null 2>&1; then
      aviso "$s aún no está en ECR: se despliega sin ese contenedor"
      filtro="$filtro | .containerDefinitions |= map(select(.name != \"$s\"))"
    fi
  done
  jq "$filtro" "$tmp" > "$tmp.json" && mv "$tmp.json" "$tmp"

  local rev; rev=$(aws ecs register-task-definition --cli-input-json "file://$tmp" \
    --query 'taskDefinition.taskDefinitionArn' --output text)
  rm -f "$tmp"
  ok "task definition registrada: ${rev##*/}"

  # Cada target group registrado apunta a su contenedor dentro de la misma tarea.
  local lb_args=()
  for s in "${SERVICIOS[@]}"; do
    local tg; tg=$(aws elbv2 describe-target-groups --names "$PROYECTO-$s" --query 'TargetGroups[0].TargetGroupArn' --output text 2>/dev/null || true)
    [[ -z "$tg" || "$tg" == "None" ]] && continue
    [[ -d "$RAIZ/$s" ]] || continue
    lb_args+=("targetGroupArn=$tg,containerName=$s,containerPort=$(puerto_de "$s")")
  done

  # Sin período de gracia, ECS mata la tarea por "unhealthy" antes de que Spring Boot levante.
  # Son 7 minutos y no 4 porque ECS mata la tarea entera si CUALQUIERA de los cuatro target
  # groups la ve enferma al vencer: la última en arrancar se llevaba puestas a las otras tres.
  if aws ecs describe-services --cluster "$CLUSTER" --services "$SERVICIO_ECS" \
       --query 'services[0].status' --output text 2>/dev/null | grep -q ACTIVE; then
    # Los balanceadores se redeclaran en cada actualización: al sumar un microservicio, su
    # target group existe pero el servicio seguía registrando solo los viejos.
    aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --force-new-deployment \
      --health-check-grace-period-seconds 420 \
      --load-balancers "${lb_args[@]}" >/dev/null
    ok "servicio actualizado (${#lb_args[@]} servicios tras el balanceador)"
  else
    aws ecs create-service --cluster "$CLUSTER" --service-name "$SERVICIO_ECS" \
      --task-definition "$rev" --desired-count 1 --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_TAREAS],assignPublicIp=ENABLED}" \
      --health-check-grace-period-seconds 420 \
      --load-balancers "${lb_args[@]}" >/dev/null
    ok "servicio creado"
  fi
  cmd_urls
}

# ---------------------------------------------------------------------------
# front — publica el frontend en S3 y lo deja accesible por HTTPS.
#
# Pasa por el API Gateway y no se sirve S3 directo porque Azure AD exige HTTPS en los URI de
# redirección, S3 es HTTP puro y CloudFront no está habilitado. El API Gateway da HTTPS y deja
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

  grep -q '^BUCKET_ADJUNTOS=' "$RAIZ/infra/.recursos" 2>/dev/null \
    || echo "BUCKET_ADJUNTOS=$bucket" >> "$RAIZ/infra/.recursos"
  azul "\nBucket de adjuntos listo: $bucket"
}

cmd_front() {
  source "$RAIZ/infra/.recursos" 2>/dev/null || { falla "Falta correr 'aws.sh crear' primero"; exit 1; }
  # Sin estos valores el front se compila igual pero el login falla, y el error recién
  # aparece en el navegador. Mejor fallar acá.
  [[ -z "${VITE_AZURE_CLIENT_ID:-}" || -z "${VITE_AZURE_SCOPE:-}" || -z "${AZURE_TENANT_ID:-}" ]] && {
    falla "Faltan VITE_AZURE_CLIENT_ID, VITE_AZURE_SCOPE o AZURE_TENANT_ID en .env"
    exit 1
  }

  local bucket="$PROYECTO-web-$(cuenta)"

  azul "1/4 · Bucket de S3"
  if ! aws s3api head-bucket --bucket "$bucket" >/dev/null 2>&1; then
    aws s3api create-bucket --bucket "$bucket" >/dev/null
  fi
  # El sitio necesita lectura pública. El contenido es el frontend compilado: no hay nada
  # sensible ahí, los identificadores de Azure son públicos por diseño del flujo.
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
  # El tipo de contenido se repite porque REPLACE borra TODA la metadata: sin esto los .js y .css
  # quedan como binary/octet-stream y el navegador los rechaza, con la página en blanco de síntoma.
  aws s3 cp "s3://$bucket/assets/" "s3://$bucket/assets/" --recursive --exclude "*" --include "*.js" \
    --metadata-directive REPLACE --content-type "text/javascript; charset=utf-8" \
    --cache-control "public, max-age=31536000, immutable" --only-show-errors
  aws s3 cp "s3://$bucket/assets/" "s3://$bucket/assets/" --recursive --exclude "*" --include "*.css" \
    --metadata-directive REPLACE --content-type "text/css; charset=utf-8" \
    --cache-control "public, max-age=31536000, immutable" --only-show-errors
  ok "subido, con tipos de contenido y caché correctos"

  azul "4/4 · Configurando el API Gateway"
  cmd_apigw

  # La URL del sitio se arma acá y no se toma de cmd_apigw: allá es una variable local
  # y desde esta función no se ve, así que el script moría al final con "unbound variable"
  # justo después de haber hecho bien todo el trabajo.
  grep -q '^SITIO_WEB=' "$RAIZ/infra/.recursos" 2>/dev/null \
    || echo "SITIO_WEB=http://$bucket.s3-website-$REGION.amazonaws.com" >> "$RAIZ/infra/.recursos"
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
  [[ -z "${AZURE_TENANT_ID:-}" || -z "${AZURE_CLIENT_ID:-}" ]] && {
    falla "Faltan AZURE_TENANT_ID o AZURE_CLIENT_ID en .env"; exit 1; }

  local dns bucket sitio emisor
  dns=$(aws elbv2 describe-load-balancers --load-balancer-arns "$ALB_ARN" \
    --query 'LoadBalancers[0].DNSName' --output text)
  bucket="$PROYECTO-web-$(cuenta)"
  sitio="http://$bucket.s3-website-$REGION.amazonaws.com"
  emisor="https://login.microsoftonline.com/$AZURE_TENANT_ID/v2.0"

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

  # --- Validador de JWT -----------------------------------------------------
  # El emisor tiene que coincidir EXACTO con el claim 'iss' del token, /v2.0 incluido.
  # La audiencia es el client-id del registro de la API en Entra ID.
  local auth_id jwt_cfg
  jwt_cfg="{\"Audience\":[\"$AZURE_CLIENT_ID\"],\"Issuer\":\"$emisor\"}"
  auth_id=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" --output json \
    | jq -r --arg n "$AUTH_NOMBRE" '.Items[] | select(.Name==$n) | .AuthorizerId' | head -1)
  if [[ -z "$auth_id" ]]; then
    auth_id=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" \
      --name "$AUTH_NOMBRE" --authorizer-type JWT \
      --identity-source '$request.header.Authorization' \
      --jwt-configuration "$jwt_cfg" --query AuthorizerId --output text)
  else
    aws apigatewayv2 update-authorizer --api-id "$API_ID" --authorizer-id "$auth_id" \
      --jwt-configuration "$jwt_cfg" >/dev/null
  fi
  ok "validador de JWT '$AUTH_NOMBRE': emisor y audiencia del tenant"

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
    [[ "${3:-}" == "jwt" ]] && extra=(--authorization-type JWT --authorizer-id "$auth_id")

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
               'ANY /api/v1/proyectos/{proxy+}' 'ANY /api/v1/colaboraciones/{proxy+}'; do
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
  integ=$(integracion "$sitio" "Frontend estatico en S3 - pagina de entrada")
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

  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" --desired-count 1 >/dev/null
  azul "Levantando la tarea (tarda ~2 min en pasar el health check)..."
  aws ecs wait services-stable --cluster "$CLUSTER" --services "$SERVICIO_ECS" && ok "tarea estable"
  cmd_urls
}

cmd_apagar() {
  aws ecs update-service --cluster "$CLUSTER" --service "$SERVICIO_ECS" --desired-count 0 >/dev/null
  ok "tareas de Fargate en cero"

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
  *) sed -n '2,25p' "$0"; exit 1 ;;
esac
