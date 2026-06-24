#!/usr/bin/env bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi
case ":${SHELLOPTS:-}:" in
  *:posix:*) exec bash "$0" "$@" ;;
esac

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
INFRA_DIR="$SCRIPT_DIR/infra"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env.prod}"
MODE="all"
MODE_SET=false
SKIP_DOCKER_PUBLISH_ARG_SET=false
SKIP_DOCKER_PUBLISH_CLI_VALUE=""
DEPLOY_TMP_DIR=""

usage() {
  cat <<'EOF'
Usage: deployment/start.sh [all|infra|docker|app] [skipDockerPublish=true|false]

Modes:
  all     Apply Terraform, publish Docker images, and deploy the EC2 Docker Compose application.
  infra   Create or update AWS EC2 infrastructure only.
  docker  Build and publish Docker images only.
  app     Deploy Docker Compose application resources to the EC2 instance only.

Options:
  skipDockerPublish=true|false
          In all mode, skip building and publishing Docker images. Default: false.
  skipDockerPublish
          Shorthand for skipDockerPublish=true.
EOF
}

for arg in "$@"; do
  case "$arg" in
    -h|--help)
      usage
      exit 0
      ;;
    all|infra|docker|app)
      if [[ "$MODE_SET" == "true" ]]; then
        echo "Mode can only be specified once." >&2
        usage >&2
        exit 1
      fi
      MODE="$arg"
      MODE_SET=true
      ;;
    skipDockerPublish)
      SKIP_DOCKER_PUBLISH_ARG_SET=true
      SKIP_DOCKER_PUBLISH_CLI_VALUE=true
      ;;
    skipDockerPublish=true|skipDockerPublish=false)
      SKIP_DOCKER_PUBLISH_ARG_SET=true
      SKIP_DOCKER_PUBLISH_CLI_VALUE="${arg#skipDockerPublish=}"
      ;;
    skipDockerPublish=*)
      echo "Invalid skipDockerPublish value: ${arg#skipDockerPublish=}. Use true or false." >&2
      usage >&2
      exit 1
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ "$SKIP_DOCKER_PUBLISH_ARG_SET" == "true" && "$MODE" != "all" ]]; then
  echo "skipDockerPublish can only be used with all mode." >&2
  usage >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from deployment/.env.prod.example first." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

if [[ "$SKIP_DOCKER_PUBLISH_ARG_SET" == "true" ]]; then
  SKIP_DOCKER_PUBLISH="$SKIP_DOCKER_PUBLISH_CLI_VALUE"
else
  SKIP_DOCKER_PUBLISH="${SKIP_DOCKER_PUBLISH:-false}"
fi

if [[ "$SKIP_DOCKER_PUBLISH" != "true" && "$SKIP_DOCKER_PUBLISH" != "false" ]]; then
  echo "Invalid skipDockerPublish value: $SKIP_DOCKER_PUBLISH. Use true or false." >&2
  usage >&2
  exit 1
fi

: "${AWS_REGION:=eu-central-1}"
: "${INFRA_NAME_PREFIX:=movie-stream}"
: "${AVAILABILITY_ZONE:=eu-central-1a}"
: "${VPC_CIDR:=10.52.0.0/16}"
: "${PUBLIC_SUBNET_CIDR:=10.52.0.0/24}"
: "${EC2_INSTANCE_TYPE:=t3.large}"
: "${EC2_AMI_ARCHITECTURE:=x86_64}"
: "${EC2_AMI_ID:=}"
: "${EC2_ROOT_VOLUME_SIZE:=30}"
: "${SSH_PUBLIC_KEY_PATH:=.ssh/movie-stream.pub}"
: "${SSH_PRIVATE_KEY_PATH:=.ssh/movie-stream}"
: "${SSH_KEY_NAME:=}"
: "${SSH_ALLOWED_CIDR:=}"
: "${EC2_SSH_USER:=ec2-user}"
: "${REMOTE_APP_DIR:=/opt/movie-stream}"
: "${MANAGE_ROUTE53_RECORD:=false}"
: "${ROUTE53_HOSTED_ZONE_ID:=}"
: "${DOCKER_SERVER:=ghcr.io}"
: "${IMAGE_VERSION:=1.2.0-movie-challenge}"
: "${KEYCLOAK_REALM:=movies}"
: "${JAVA_OPTS:=-XX:MaxRAMPercentage=75}"
: "${MOVIES_POSTGRES_HOST_DIR:=/mnt/movie-stream/movies-postgres}"
: "${KEYCLOAK_POSTGRES_HOST_DIR:=/mnt/movie-stream/keycloak-postgres}"
: "${PGADMIN_HOST_DIR:=/mnt/movie-stream/pgadmin}"
: "${MOVIES_POSTGRES_DEVICE_NAME:=/dev/sdf}"
: "${KEYCLOAK_POSTGRES_DEVICE_NAME:=/dev/sdg}"

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

expand_path() {
  local path="$1"
  if [[ "$path" == "~/"* ]]; then
    printf '%s/%s' "$HOME" "${path#~/}"
  elif [[ "$path" == /* ]]; then
    printf '%s' "$path"
  else
    printf '%s/%s' "$ROOT_DIR" "$path"
  fi
}

configure_terraform_vars() {
  export TF_VAR_aws_region="$AWS_REGION"
  export TF_VAR_name_prefix="$INFRA_NAME_PREFIX"
  export TF_VAR_availability_zone="$AVAILABILITY_ZONE"
  export TF_VAR_vpc_cidr="$VPC_CIDR"
  export TF_VAR_public_subnet_cidr="$PUBLIC_SUBNET_CIDR"
  export TF_VAR_ec2_instance_type="$EC2_INSTANCE_TYPE"
  export TF_VAR_ec2_ami_architecture="$EC2_AMI_ARCHITECTURE"
  export TF_VAR_ec2_ami_id="$EC2_AMI_ID"
  export TF_VAR_ec2_root_volume_size="$EC2_ROOT_VOLUME_SIZE"
  export TF_VAR_ssh_allowed_cidr="$SSH_ALLOWED_CIDR"
  export TF_VAR_ssh_key_name="$SSH_KEY_NAME"
  export TF_VAR_ssh_public_key_path
  TF_VAR_ssh_public_key_path="$(expand_path "$SSH_PUBLIC_KEY_PATH")"
  export TF_VAR_app_domain="${APP_DOMAIN:-}"
  export TF_VAR_manage_route53_record="$MANAGE_ROUTE53_RECORD"
  export TF_VAR_route53_hosted_zone_id="$ROUTE53_HOSTED_ZONE_ID"
  export TF_VAR_movies_postgres_volume_id="${MOVIES_POSTGRES_VOLUME_ID:-}"
  export TF_VAR_keycloak_postgres_volume_id="${KEYCLOAK_POSTGRES_VOLUME_ID:-}"
  export TF_VAR_movies_postgres_device_name="$MOVIES_POSTGRES_DEVICE_NAME"
  export TF_VAR_keycloak_postgres_device_name="$KEYCLOAK_POSTGRES_DEVICE_NAME"
}

apply_infra() {
  require_cmd terraform
  require_cmd aws
  require_var SSH_ALLOWED_CIDR
  if [[ -z "$SSH_KEY_NAME" ]]; then
    local public_key_path
    public_key_path="$(expand_path "$SSH_PUBLIC_KEY_PATH")"
    if [[ ! -f "$public_key_path" ]]; then
      echo "SSH_PUBLIC_KEY_PATH does not exist: $public_key_path" >&2
      echo "Create a deployment key with: mkdir -p .ssh && ssh-keygen -t ed25519 -C movie-stream-deploy -f .ssh/movie-stream -N ''" >&2
      echo "Set SSH_PUBLIC_KEY_PATH to an existing .pub file, or set SSH_KEY_NAME to an existing EC2 key pair name." >&2
      exit 1
    fi
  fi
  configure_terraform_vars

  terraform -chdir="$INFRA_DIR" init
  terraform -chdir="$INFRA_DIR" apply -auto-approve

  echo "Infrastructure is ready."
  echo "EC2 instance ID: $(terraform -chdir="$INFRA_DIR" output -raw instance_id)"
  echo "Elastic IP: $(terraform -chdir="$INFRA_DIR" output -raw elastic_ip)"
  echo "Availability Zone: $(terraform -chdir="$INFRA_DIR" output -raw availability_zone)"
}

publish_images() {
  require_cmd docker
  "$SCRIPT_DIR/docker-publish.sh"
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

quote_env_value() {
  local value="$1"
  value="${value//\'/\'\\\'\'}"
  printf "'%s'" "$value"
}

write_env_var() {
  local output_file="$1"
  local var="$2"
  printf '%s=' "$var" >>"$output_file"
  quote_env_value "${!var}" >>"$output_file"
  printf '\n' >>"$output_file"
}

render_remote_env() {
  local output_file="$1"
  : >"$output_file"

  local vars=(
    APP_DOMAIN
    APP_BASE_URL
    APP_CORS_ALLOWED_ORIGINS
    CONTAINER_REGISTRY
    DOCKER_SERVER
    GITHUB_USERNAME
    GITHUB_TOKEN
    IMAGE_VERSION
    KEYCLOAK_EXTERNAL_URL
    KEYCLOAK_ISSUER_URI
    KEYCLOAK_TOKEN_URI
    KEYCLOAK_REALM
    KEYCLOAK_POSTGRES_DB
    KEYCLOAK_POSTGRES_USER
    KEYCLOAK_POSTGRES_PASSWORD
    KEYCLOAK_ADMIN
    KEYCLOAK_ADMIN_PASSWORD
    MOVIES_JDBC_URL
    MOVIES_JDBC_USERNAME
    MOVIES_JDBC_PASSWORD
    MOVIES_POSTGRES_DB
    MOVIES_POSTGRES_HOST_DIR
    MOVIES_POSTGRES_VOLUME_ID
    MOVIES_POSTGRES_DEVICE_NAME
    KEYCLOAK_POSTGRES_HOST_DIR
    KEYCLOAK_POSTGRES_VOLUME_ID
    KEYCLOAK_POSTGRES_DEVICE_NAME
    PGADMIN_DEFAULT_EMAIL
    PGADMIN_DEFAULT_PASSWORD
    PGADMIN_HOST_DIR
    JAVA_OPTS
  )

  local var
  for var in "${vars[@]}"; do
    write_env_var "$output_file" "$var"
  done
}

cleanup_deploy_tmp_dir() {
  if [[ -n "${DEPLOY_TMP_DIR:-}" ]]; then
    rm -rf "$DEPLOY_TMP_DIR"
  fi
}

terraform_output() {
  terraform -chdir="$INFRA_DIR" output -raw "$1"
}

remote_host() {
  terraform_output elastic_ip
}

remote_target() {
  printf '%s@%s' "$EC2_SSH_USER" "$(remote_host)"
}

wait_for_ssh() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  echo "Waiting for SSH on $target ..."
  for _ in {1..60}; do
    if ssh -i "$key_path" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 "$target" "true" >/dev/null 2>&1; then
      return
    fi
    sleep 5
  done

  echo "Timed out waiting for SSH on $target." >&2
  exit 1
}

remote_run() {
  local target="$1"
  shift
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"
  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" "$@"
}

copy_to_remote() {
  local target="$1"
  shift
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"
  scp -i "$key_path" -o StrictHostKeyChecking=accept-new "$@" "$target:$REMOTE_APP_DIR/"
}

copy_keycloak_theme_to_remote() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  remote_run "$target" "mkdir -p '$REMOTE_APP_DIR/keycloak-theme/login'"
  scp -i "$key_path" -o StrictHostKeyChecking=accept-new \
    "$ROOT_DIR/config/keycloak-themes/movie-stream/login/theme.properties" \
    "$ROOT_DIR/config/keycloak-themes/movie-stream/login/register.ftl" \
    "$target:$REMOTE_APP_DIR/keycloak-theme/login/"
}

prepare_remote_host() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" \
    "REMOTE_APP_DIR=$(quote_env_value "$REMOTE_APP_DIR") EC2_SSH_USER=$(quote_env_value "$EC2_SSH_USER") bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail

if command -v cloud-init >/dev/null 2>&1; then
  if ! cloud-init status --wait >/dev/null 2>&1; then
    echo "cloud-init did not finish cleanly; continuing with explicit host setup."
  fi
fi

install_packages() {
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y docker util-linux xfsprogs
  elif command -v yum >/dev/null 2>&1; then
    sudo yum install -y docker util-linux xfsprogs
  else
    echo "Could not install host dependencies: dnf or yum is required." >&2
    exit 1
  fi
}

install_curl_minimal() {
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y curl-minimal
  elif command -v yum >/dev/null 2>&1; then
    sudo yum install -y curl-minimal
  else
    echo "Could not install curl-minimal: dnf or yum is required." >&2
    exit 1
  fi
}

if ! command -v docker >/dev/null 2>&1 \
  || ! command -v lsblk >/dev/null 2>&1 \
  || ! command -v mkfs.xfs >/dev/null 2>&1; then
  install_packages
fi

if ! command -v curl >/dev/null 2>&1; then
  install_curl_minimal
fi

sudo systemctl enable --now docker

if ! sudo docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  arch="$(uname -m)"
  case "$arch" in
    x86_64) compose_arch="x86_64" ;;
    aarch64) compose_arch="aarch64" ;;
    *) echo "Unsupported architecture for Docker Compose: $arch" >&2; exit 1 ;;
  esac
  sudo curl -fsSL \
    "https://github.com/docker/compose/releases/download/v2.40.3/docker-compose-linux-$compose_arch" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

sudo docker version >/dev/null
sudo docker compose version >/dev/null
sudo mkdir -p "$REMOTE_APP_DIR"
sudo chown "$EC2_SSH_USER:$EC2_SSH_USER" "$REMOTE_APP_DIR"
REMOTE_SCRIPT
}

mount_remote_volumes() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" "cd '$REMOTE_APP_DIR' && bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail
set -a
# shellcheck source=/dev/null
. ./.env
set +a

resolve_device() {
  local volume_id="$1"
  local requested_device="$2"
  local normalized="${volume_id//-/}"
  local candidate

  for candidate in \
    "/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${normalized}" \
    "/dev/disk/by-id/nvme-Amazon_Elastic_Block_Store_${volume_id}" \
    "$requested_device" \
    "${requested_device/sd/xvd}"; do
    if [[ -e "$candidate" ]]; then
      readlink -f "$candidate"
      return 0
    fi
  done

  while read -r name serial; do
    if [[ "${serial//-/}" == "$normalized" ]]; then
      printf '/dev/%s\n' "$name"
      return 0
    fi
  done < <(lsblk -ndo NAME,SERIAL 2>/dev/null || true)

  return 1
}

mount_volume() {
  local volume_id="$1"
  local requested_device="$2"
  local mount_point="$3"
  local owner="$4"
  local device=""

  for _ in {1..30}; do
    if device="$(resolve_device "$volume_id" "$requested_device")"; then
      break
    fi
    sleep 2
  done

  if [[ -z "$device" ]]; then
    echo "Could not resolve attached EBS volume $volume_id." >&2
    exit 1
  fi

  if ! sudo blkid "$device" >/dev/null 2>&1; then
    sudo mkfs.xfs -f "$device"
  fi

  local uuid
  local fs_type
  uuid="$(sudo blkid -s UUID -o value "$device")"
  fs_type="$(sudo blkid -s TYPE -o value "$device")"

  sudo mkdir -p "$mount_point"
  if ! grep -q "UUID=$uuid " /etc/fstab; then
    echo "UUID=$uuid $mount_point $fs_type defaults,nofail 0 2" | sudo tee -a /etc/fstab >/dev/null
  fi

  if ! findmnt -rn "$mount_point" >/dev/null 2>&1; then
    sudo mount "$mount_point"
  fi

  sudo chown -R "$owner" "$mount_point"
}

mount_volume "$MOVIES_POSTGRES_VOLUME_ID" "$MOVIES_POSTGRES_DEVICE_NAME" "$MOVIES_POSTGRES_HOST_DIR" "999:999"
mount_volume "$KEYCLOAK_POSTGRES_VOLUME_ID" "$KEYCLOAK_POSTGRES_DEVICE_NAME" "$KEYCLOAK_POSTGRES_HOST_DIR" "999:999"

sudo mkdir -p "$PGADMIN_HOST_DIR"
sudo chown -R 5050:5050 "$PGADMIN_HOST_DIR"
REMOTE_SCRIPT
}

run_remote_compose() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" "cd '$REMOTE_APP_DIR' && bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail
set -a
# shellcheck source=/dev/null
. ./.env
set +a

echo "$GITHUB_TOKEN" | sudo docker login "$DOCKER_SERVER" -u "$GITHUB_USERNAME" --password-stdin
sudo docker compose --env-file .env -f docker-compose.prod.yml pull
sudo docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
sudo docker compose --env-file .env -f docker-compose.prod.yml ps
REMOTE_SCRIPT
}

configure_remote_keycloak_registration() {
  local target="$1"
  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"

  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" "cd '$REMOTE_APP_DIR' && bash -s" <<'REMOTE_SCRIPT'
set -euo pipefail
set -a
# shellcheck source=/dev/null
. ./.env
set +a

configured=false
for _ in {1..30}; do
  if sudo docker compose --env-file .env -f docker-compose.prod.yml exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh config credentials \
      --server http://localhost:8080/keycloak \
      --realm master \
      --user "$KEYCLOAK_ADMIN" \
      --password "$KEYCLOAK_ADMIN_PASSWORD" >/dev/null 2>&1; then
    configured=true
    break
  fi
  sleep 5
done

if [[ "$configured" != "true" ]]; then
  echo "Could not authenticate to Keycloak admin API." >&2
  exit 1
fi

sudo docker compose --env-file .env -f docker-compose.prod.yml exec -T -e KEYCLOAK_REALM="$KEYCLOAK_REALM" keycloak bash -s <<'KEYCLOAK_SCRIPT'
set -euo pipefail
cat >/tmp/movie-stream-user-profile.json <<'USER_PROFILE_JSON'
{
  "attributes": [
    {
      "name": "username",
      "displayName": "${username}",
      "validations": {
        "length": {
          "min": 3,
          "max": 255
        },
        "username-prohibited-characters": {},
        "up-username-not-idn-homograph": {}
      },
      "permissions": {
        "view": [
          "admin",
          "user"
        ],
        "edit": [
          "admin",
          "user"
        ]
      },
      "multivalued": false
    },
    {
      "name": "email",
      "displayName": "${email}",
      "validations": {
        "email": {},
        "length": {
          "max": 320
        }
      },
      "required": {
        "roles": [
          "user"
        ]
      },
      "permissions": {
        "view": [
          "admin",
          "user"
        ],
        "edit": [
          "admin",
          "user"
        ]
      },
      "multivalued": false
    }
  ],
  "groups": [
    {
      "name": "user-metadata",
      "displayHeader": "User metadata",
      "displayDescription": "Attributes, which refer to user metadata"
    }
  ]
}
USER_PROFILE_JSON
/opt/keycloak/bin/kcadm.sh update users/profile -r "$KEYCLOAK_REALM" -f /tmp/movie-stream-user-profile.json
KEYCLOAK_SCRIPT

sudo docker compose --env-file .env -f docker-compose.prod.yml exec -T keycloak \
  /opt/keycloak/bin/kcadm.sh update "realms/$KEYCLOAK_REALM" \
    -s accessTokenLifespan=315360000 \
    -s accessTokenLifespanForImplicitFlow=315360000 \
    -s ssoSessionIdleTimeout=315360000 \
    -s ssoSessionMaxLifespan=315360000 \
    -s clientSessionIdleTimeout=315360000 \
    -s clientSessionMaxLifespan=315360000 \
    -s loginTheme=movie-stream \
    -s loginWithEmailAllowed=false \
    -s duplicateEmailsAllowed=false \
    -s verifyEmail=false

for username in user admin; do
  user_id="$(sudo docker compose --env-file .env -f docker-compose.prod.yml exec -T keycloak \
    /opt/keycloak/bin/kcadm.sh get users \
      -r "$KEYCLOAK_REALM" \
      -q username="$username" \
      --fields id \
      --format csv \
      --noquotes | tr -d '\r' | head -n 1)"
  if [[ -n "$user_id" ]]; then
    sudo docker compose --env-file .env -f docker-compose.prod.yml exec -T keycloak \
      /opt/keycloak/bin/kcadm.sh update "users/$user_id" \
        -r "$KEYCLOAK_REALM" \
        -s firstName= \
        -s lastName=
  fi
done
REMOTE_SCRIPT
}

deploy_app() {
  require_cmd terraform
  require_cmd ssh
  require_cmd scp
  require_cmd envsubst

  local required_app_vars=(
    APP_DOMAIN
    CONTAINER_REGISTRY
    GITHUB_USERNAME
    GITHUB_TOKEN
    OMDB_API_KEY
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

  local var
  for var in "${required_app_vars[@]}"; do
    require_var "$var"
  done

  CONTAINER_REGISTRY="${CONTAINER_REGISTRY%/}"
  APP_DOMAIN="${APP_DOMAIN#https://}"
  APP_DOMAIN="${APP_DOMAIN#http://}"
  APP_DOMAIN="${APP_DOMAIN%/}"
  APP_BASE_URL="${APP_BASE_URL:-https://$APP_DOMAIN}"
  APP_BASE_URL="${APP_BASE_URL%/}"
  KEYCLOAK_EXTERNAL_URL="${KEYCLOAK_EXTERNAL_URL:-$APP_BASE_URL/keycloak}"
  KEYCLOAK_EXTERNAL_URL="${KEYCLOAK_EXTERNAL_URL%/}"
  APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-$APP_BASE_URL}"
  MOVIES_JDBC_URL="${MOVIES_JDBC_URL:-jdbc:postgresql://postgres-movies:5432/movies}"
  if [[ "$MOVIES_JDBC_URL" == jdbc:postgresql://movies-postgres:* ]]; then
    MOVIES_JDBC_URL="${MOVIES_JDBC_URL/movies-postgres/postgres-movies}"
  fi
  KEYCLOAK_ISSUER_URI="${KEYCLOAK_ISSUER_URI:-$KEYCLOAK_EXTERNAL_URL/realms/$KEYCLOAK_REALM}"
  KEYCLOAK_TOKEN_URI="${KEYCLOAK_TOKEN_URI:-$KEYCLOAK_EXTERNAL_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token}"

  export APP_DOMAIN APP_BASE_URL APP_CORS_ALLOWED_ORIGINS CONTAINER_REGISTRY
  export KEYCLOAK_EXTERNAL_URL KEYCLOAK_ISSUER_URI KEYCLOAK_TOKEN_URI KEYCLOAK_REALM
  export MOVIES_JDBC_URL

  derive_movies_postgres_db

  DEPLOY_TMP_DIR="$(mktemp -d)"
  trap cleanup_deploy_tmp_dir EXIT

  render_app_config "$DEPLOY_TMP_DIR/app-config.json"
  envsubst '${APP_BASE_URL}' <"$SCRIPT_DIR/keycloak-realm.template.json" >"$DEPLOY_TMP_DIR/keycloak-realm.json"
  envsubst '${APP_DOMAIN}' <"$SCRIPT_DIR/Caddyfile.template" >"$DEPLOY_TMP_DIR/Caddyfile"
  render_remote_env "$DEPLOY_TMP_DIR/.env"

  local target
  target="$(remote_target)"
  wait_for_ssh "$target"
  prepare_remote_host "$target"
  copy_to_remote "$target" \
    "$SCRIPT_DIR/docker-compose.prod.yml" \
    "$DEPLOY_TMP_DIR/.env" \
    "$DEPLOY_TMP_DIR/Caddyfile" \
    "$DEPLOY_TMP_DIR/app-config.json" \
    "$DEPLOY_TMP_DIR/keycloak-realm.json"
  copy_keycloak_theme_to_remote "$target"

  mount_remote_volumes "$target"
  run_remote_compose "$target"
  configure_remote_keycloak_registration "$target"

  echo "Application deployed."
  echo "Movie Stream URL: $APP_BASE_URL"
  echo "Gateway API URL: $APP_BASE_URL/api"
  echo "Keycloak URL: $KEYCLOAK_EXTERNAL_URL"
  echo "pgAdmin URL: $APP_BASE_URL/pgadmin"
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
    if [[ "$SKIP_DOCKER_PUBLISH" == "true" ]]; then
      echo "Skipping Docker image publish."
    else
      publish_images
    fi
    deploy_app
    ;;
esac
