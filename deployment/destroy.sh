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
: "${SSH_ALLOWED_CIDR:=0.0.0.0/0}"
: "${EC2_SSH_USER:=ec2-user}"
: "${REMOTE_APP_DIR:=/opt/movie-stream}"
: "${MANAGE_ROUTE53_RECORD:=false}"
: "${ROUTE53_HOSTED_ZONE_ID:=}"
: "${MOVIES_POSTGRES_DEVICE_NAME:=/dev/sdf}"
: "${KEYCLOAK_POSTGRES_DEVICE_NAME:=/dev/sdg}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' is not installed or not on PATH." >&2
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

cleanup_remote_app() {
  require_cmd ssh

  local host
  host="$(terraform -chdir="$INFRA_DIR" output -raw elastic_ip 2>/dev/null || true)"
  if [[ -z "$host" ]]; then
    echo "No EC2 output found; skipping remote Docker Compose cleanup."
    return
  fi

  local key_path
  key_path="$(expand_path "$SSH_PRIVATE_KEY_PATH")"
  local target="$EC2_SSH_USER@$host"

  if ! ssh -i "$key_path" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 "$target" "true" >/dev/null 2>&1; then
    echo "EC2 instance is not reachable over SSH; skipping remote Docker Compose cleanup."
    return
  fi

  ssh -i "$key_path" -o StrictHostKeyChecking=accept-new "$target" "cd '$REMOTE_APP_DIR' 2>/dev/null && bash -s" <<'REMOTE_SCRIPT' || true
set -euo pipefail

if [[ -f .env ]]; then
  set -a
  # shellcheck source=/dev/null
  . ./.env
  set +a
fi

if [[ -f docker-compose.prod.yml ]]; then
  sudo docker compose --env-file .env -f docker-compose.prod.yml down --remove-orphans || true
fi

for mount_point in "${MOVIES_POSTGRES_HOST_DIR:-}" "${KEYCLOAK_POSTGRES_HOST_DIR:-}"; do
  if [[ -n "$mount_point" ]] && findmnt -rn "$mount_point" >/dev/null 2>&1; then
    sudo umount "$mount_point" || true
  fi
done
REMOTE_SCRIPT
}

destroy_infra() {
  require_cmd terraform
  configure_terraform_vars
  terraform -chdir="$INFRA_DIR" init
  cleanup_remote_app
  terraform -chdir="$INFRA_DIR" destroy -auto-approve
}

destroy_infra

echo "Terraform-managed EC2 infrastructure destroyed. Existing database EBS volumes were only detached; they were not deleted."
