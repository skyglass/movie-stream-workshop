#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
INFRA_DIR="$SCRIPT_DIR/infra"
K8S_DIR="$SCRIPT_DIR/k8s"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env.prod}"
MODE="${1:-all}"

usage() {
  cat <<'EOF'
Usage: deployment/start.sh [all|infra|docker|app]

Modes:
  all     Apply Terraform, publish Docker images, and deploy Kubernetes resources.
  infra   Create or update AWS infrastructure only.
  docker  Build and publish Docker images only.
  app     Deploy Kubernetes resources only.
EOF
}

if [[ "$MODE" == "-h" || "$MODE" == "--help" ]]; then
  usage
  exit 0
fi

case "$MODE" in
  all|infra|docker|app) ;;
  *)
    usage >&2
    exit 1
    ;;
esac

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from deployment/.env.prod.example first." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

: "${AWS_REGION:=eu-central-1}"
: "${INFRA_NAME_PREFIX:=movie-stream}"
: "${CLUSTER_VERSION:=1.32}"
: "${NODE_INSTANCE_TYPE:=m6i.large}"
: "${NODE_MIN_SIZE:=2}"
: "${NODE_DESIRED_SIZE:=2}"
: "${NODE_MAX_SIZE:=4}"
: "${SINGLE_NAT_GATEWAY:=true}"
: "${STATEFUL_AVAILABILITY_ZONE:=eu-central-1a}"
: "${STATEFUL_NODE_INSTANCE_TYPE:=m6i.large}"
: "${STATEFUL_NODE_MIN_SIZE:=1}"
: "${STATEFUL_NODE_DESIRED_SIZE:=1}"
: "${STATEFUL_NODE_MAX_SIZE:=2}"
: "${APP_NAMESPACE:=movie-stream}"
: "${IMAGE_VERSION:=1.2.0-movie-challenge}"
: "${MOVIES_POSTGRES_STORAGE_SIZE:=70Gi}"
: "${KEYCLOAK_POSTGRES_STORAGE_SIZE:=10Gi}"
: "${JAVA_OPTS:=-XX:MaxRAMPercentage=75}"
: "${KEYCLOAK_REALM:=movies}"
: "${DOCKER_SERVER:=ghcr.io}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' is not installed or not on PATH." >&2
    exit 1
  fi
}

require_var() {
  local var="$1"
  if [[ -z "${!var:-}" ]]; then
    echo "Required variable $var is missing in $ENV_FILE." >&2
    exit 1
  fi
}

configure_terraform_vars() {
  export TF_VAR_aws_region="$AWS_REGION"
  export TF_VAR_name_prefix="$INFRA_NAME_PREFIX"
  export TF_VAR_cluster_version="$CLUSTER_VERSION"
  export TF_VAR_node_instance_types="[\"$NODE_INSTANCE_TYPE\"]"
  export TF_VAR_node_min_size="$NODE_MIN_SIZE"
  export TF_VAR_node_desired_size="$NODE_DESIRED_SIZE"
  export TF_VAR_node_max_size="$NODE_MAX_SIZE"
  export TF_VAR_single_nat_gateway="$SINGLE_NAT_GATEWAY"
  export TF_VAR_stateful_availability_zone="$STATEFUL_AVAILABILITY_ZONE"
  export TF_VAR_stateful_node_instance_types="[\"$STATEFUL_NODE_INSTANCE_TYPE\"]"
  export TF_VAR_stateful_node_min_size="$STATEFUL_NODE_MIN_SIZE"
  export TF_VAR_stateful_node_desired_size="$STATEFUL_NODE_DESIRED_SIZE"
  export TF_VAR_stateful_node_max_size="$STATEFUL_NODE_MAX_SIZE"
}

apply_infra() {
  require_cmd terraform
  require_cmd aws
  configure_terraform_vars

  terraform -chdir="$INFRA_DIR" init
  terraform -chdir="$INFRA_DIR" apply -auto-approve

  local cluster_name
  cluster_name="$(terraform -chdir="$INFRA_DIR" output -raw cluster_name)"
  aws eks update-kubeconfig --region "$AWS_REGION" --name "$cluster_name"

  echo "Infrastructure is ready for cluster $cluster_name."
  echo "VPC ID: $(terraform -chdir="$INFRA_DIR" output -raw vpc_id)"
  echo "Stateful AZ: $(terraform -chdir="$INFRA_DIR" output -raw stateful_availability_zone)"
  echo "Stateful subnet ID: $(terraform -chdir="$INFRA_DIR" output -raw stateful_private_subnet_id)"
}

publish_images() {
  require_cmd docker
  "$SCRIPT_DIR/docker-publish.sh"
}

validate_ebs_volume() {
  local var="$1"
  local value="${!var:-}"

  if [[ -z "$value" ]]; then
    echo "$var must be set in $ENV_FILE." >&2
    exit 1
  fi

  if [[ ! "$value" =~ ^vol-[a-zA-Z0-9]+$ ]]; then
    echo "$var must look like vol-0123456789abcdef0. Current value: $value" >&2
    exit 1
  fi
}

derive_movies_postgres_db() {
  if [[ "$MOVIES_JDBC_URL" =~ ^jdbc:postgresql://[^/]+/([^?]+) ]]; then
    MOVIES_POSTGRES_DB="${BASH_REMATCH[1]}"
  else
    echo "MOVIES_JDBC_URL must look like jdbc:postgresql://host:port/database. Current value: $MOVIES_JDBC_URL" >&2
    exit 1
  fi

  if [[ -z "$MOVIES_POSTGRES_DB" ]]; then
    echo "Could not derive the movie database name from MOVIES_JDBC_URL." >&2
    exit 1
  fi

  export MOVIES_POSTGRES_DB
}

render_app_config() {
  local output_file="$1"

  cat >"$output_file" <<EOF
{
  "apiBaseUrl": "$APP_BASE_URL",
  "authTokenPath": "/auth/token",
  "clientId": "movies-ui",
  "keycloakBaseUrl": "$KEYCLOAK_EXTERNAL_URL",
  "keycloakRealm": "$KEYCLOAK_REALM",
  "uiBaseUrl": "$APP_BASE_URL",
  "moviesApiPath": "/api/movies/movies",
  "movieChallengesPath": "/api/movies/movie-challenges",
  "favoriteMoviesPath": "/api/movies/favorite-movies",
  "usersFavoriteMoviesPath": "/api/movies/users-favorite-movies",
  "userExtrasPath": "/api/movies/userextras",
  "usersApiPath": "/api/movies/users",
  "omdbBaseUrl": "https://www.omdbapi.com/",
  "omdbApiKey": "$OMDB_API_KEY",
  "pricingApiPath": "",
  "pricingEventsPath": "",
  "competitorApiPath": "",
  "accountApiPath": "",
  "transferApiPath": "",
  "wsPath": "/api/movies/ws"
}
EOF
}

create_runtime_resources() {
  local tmp_dir="$1"
  local ui_config="$tmp_dir/app-config.json"
  local realm_config="$tmp_dir/realm-movies.json"

  render_app_config "$ui_config"
  envsubst '${APP_BASE_URL}' \
    < "$K8S_DIR/keycloak-realm.template.json" > "$realm_config"

  kubectl create namespace "$APP_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

  kubectl -n "$APP_NAMESPACE" create secret docker-registry ghcr-pull-secret \
    --docker-server="$DOCKER_SERVER" \
    --docker-username="$GITHUB_USERNAME" \
    --docker-password="$GITHUB_TOKEN" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl -n "$APP_NAMESPACE" create secret generic movie-stream-secrets \
    --from-literal=MOVIES_POSTGRES_DB="$MOVIES_POSTGRES_DB" \
    --from-literal=MOVIES_JDBC_USERNAME="$MOVIES_JDBC_USERNAME" \
    --from-literal=MOVIES_JDBC_PASSWORD="$MOVIES_JDBC_PASSWORD" \
    --from-literal=KEYCLOAK_POSTGRES_DB="$KEYCLOAK_POSTGRES_DB" \
    --from-literal=KEYCLOAK_POSTGRES_USER="$KEYCLOAK_POSTGRES_USER" \
    --from-literal=KEYCLOAK_POSTGRES_PASSWORD="$KEYCLOAK_POSTGRES_PASSWORD" \
    --from-literal=KEYCLOAK_ADMIN="$KEYCLOAK_ADMIN" \
    --from-literal=KEYCLOAK_ADMIN_PASSWORD="$KEYCLOAK_ADMIN_PASSWORD" \
    --from-literal=PGADMIN_DEFAULT_EMAIL="$PGADMIN_DEFAULT_EMAIL" \
    --from-literal=PGADMIN_DEFAULT_PASSWORD="$PGADMIN_DEFAULT_PASSWORD" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl -n "$APP_NAMESPACE" create configmap movie-stream-config \
    --from-literal=APP_BASE_URL="$APP_BASE_URL" \
    --from-literal=APP_CORS_ALLOWED_ORIGINS="$APP_CORS_ALLOWED_ORIGINS" \
    --from-literal=KEYCLOAK_EXTERNAL_URL="$KEYCLOAK_EXTERNAL_URL" \
    --from-literal=KEYCLOAK_ISSUER_URI="$KEYCLOAK_ISSUER_URI" \
    --from-literal=KEYCLOAK_TOKEN_URI="$KEYCLOAK_TOKEN_URI" \
    --from-literal=MOVIES_JDBC_URL="$MOVIES_JDBC_URL" \
    --from-literal=JAVA_OPTS="$JAVA_OPTS" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl -n "$APP_NAMESPACE" create configmap movies-ui-config \
    --from-file=app-config.json="$ui_config" \
    --dry-run=client -o yaml | kubectl apply -f -

  kubectl -n "$APP_NAMESPACE" create configmap keycloak-realm \
    --from-file=realm-movies.json="$realm_config" \
    --dry-run=client -o yaml | kubectl apply -f -
}

apply_manifest() {
  local file="$1"
  envsubst '${APP_NAMESPACE} ${CONTAINER_REGISTRY} ${IMAGE_VERSION} ${MOVIES_POSTGRES_STORAGE_SIZE} ${KEYCLOAK_POSTGRES_STORAGE_SIZE} ${MOVIES_POSTGRES_VOLUME_ID} ${MOVIES_POSTGRES_VOLUME_AZ} ${KEYCLOAK_POSTGRES_VOLUME_ID} ${KEYCLOAK_POSTGRES_VOLUME_AZ} ${AWS_ACM_CERTIFICATE_ARN} ${APP_DOMAIN} ${KEYCLOAK_DOMAIN}' \
    < "$file" | kubectl apply -f -
}

deploy_app() {
  require_cmd aws
  require_cmd kubectl
  require_cmd envsubst

  required_app_vars=(
    APP_DOMAIN
    AWS_ACM_CERTIFICATE_ARN
    CONTAINER_REGISTRY
    GITHUB_USERNAME
    GITHUB_TOKEN
    OMDB_API_KEY
    MOVIES_JDBC_URL
    MOVIES_JDBC_USERNAME
    MOVIES_JDBC_PASSWORD
    KEYCLOAK_POSTGRES_DB
    KEYCLOAK_POSTGRES_USER
    KEYCLOAK_POSTGRES_PASSWORD
    KEYCLOAK_ADMIN
    KEYCLOAK_ADMIN_PASSWORD
    PGADMIN_DEFAULT_EMAIL
    PGADMIN_DEFAULT_PASSWORD
    MOVIES_POSTGRES_VOLUME_ID
    KEYCLOAK_POSTGRES_VOLUME_ID
  )

  for var in "${required_app_vars[@]}"; do
    require_var "$var"
  done

  validate_ebs_volume MOVIES_POSTGRES_VOLUME_ID
  validate_ebs_volume KEYCLOAK_POSTGRES_VOLUME_ID
  derive_movies_postgres_db

  CONTAINER_REGISTRY="${CONTAINER_REGISTRY%/}"
  APP_BASE_URL="${APP_BASE_URL:-https://$APP_DOMAIN}"
  KEYCLOAK_DOMAIN="${KEYCLOAK_DOMAIN:-keycloak.$APP_DOMAIN}"
  KEYCLOAK_EXTERNAL_URL="${KEYCLOAK_EXTERNAL_URL:-https://$KEYCLOAK_DOMAIN}"
  APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-$APP_BASE_URL}"
  KEYCLOAK_ISSUER_URI="${KEYCLOAK_ISSUER_URI:-http://keycloak:8080/realms/$KEYCLOAK_REALM}"
  KEYCLOAK_TOKEN_URI="${KEYCLOAK_TOKEN_URI:-http://keycloak:8080/realms/$KEYCLOAK_REALM/protocol/openid-connect/token}"
  MOVIES_POSTGRES_VOLUME_AZ="${MOVIES_POSTGRES_VOLUME_AZ:-$STATEFUL_AVAILABILITY_ZONE}"
  KEYCLOAK_POSTGRES_VOLUME_AZ="${KEYCLOAK_POSTGRES_VOLUME_AZ:-$STATEFUL_AVAILABILITY_ZONE}"

  export APP_BASE_URL KEYCLOAK_EXTERNAL_URL APP_CORS_ALLOWED_ORIGINS KEYCLOAK_ISSUER_URI KEYCLOAK_TOKEN_URI CONTAINER_REGISTRY
  export KEYCLOAK_REALM KEYCLOAK_DOMAIN
  export APP_NAMESPACE IMAGE_VERSION MOVIES_POSTGRES_STORAGE_SIZE KEYCLOAK_POSTGRES_STORAGE_SIZE AWS_ACM_CERTIFICATE_ARN APP_DOMAIN
  export MOVIES_POSTGRES_VOLUME_ID MOVIES_POSTGRES_VOLUME_AZ KEYCLOAK_POSTGRES_VOLUME_ID KEYCLOAK_POSTGRES_VOLUME_AZ

  local tmp_dir
  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT

  create_runtime_resources "$tmp_dir"

  apply_manifest "$K8S_DIR/namespace.yaml"
  apply_manifest "$K8S_DIR/postgres.yaml"
  apply_manifest "$K8S_DIR/keycloak.yaml"
  apply_manifest "$K8S_DIR/apps.yaml"
  apply_manifest "$K8S_DIR/pgadmin.yaml"
  apply_manifest "$K8S_DIR/ingress.yaml"

  kubectl -n "$APP_NAMESPACE" rollout status statefulset/movies-postgres --timeout=10m
  kubectl -n "$APP_NAMESPACE" rollout status statefulset/keycloak-postgres --timeout=10m
  kubectl -n "$APP_NAMESPACE" rollout status deployment/keycloak --timeout=15m
  kubectl -n "$APP_NAMESPACE" rollout status deployment/movies-api --timeout=10m
  kubectl -n "$APP_NAMESPACE" rollout status deployment/movie-gateway --timeout=10m
  kubectl -n "$APP_NAMESPACE" rollout status deployment/movies-ui --timeout=10m
  kubectl -n "$APP_NAMESPACE" rollout status deployment/pgadmin --timeout=10m

  echo "Application deployment submitted."
  echo "Movie Stream URL: $APP_BASE_URL"
  echo "Keycloak URL: $KEYCLOAK_EXTERNAL_URL"
  echo "pgAdmin URL: $APP_BASE_URL/pgadmin"
  echo "Point DNS for $APP_DOMAIN to the ALB hostname shown by:"
  echo "kubectl -n $APP_NAMESPACE get ingress movie-stream"
}

case "$MODE" in
  infra)
    apply_infra
    ;;
  docker)
    publish_images
    ;;
  app)
    deploy_app
    ;;
  all)
    apply_infra
    if [[ "${SKIP_DOCKER_PUBLISH:-false}" != "true" ]]; then
      publish_images
    fi
    deploy_app
    ;;
esac
