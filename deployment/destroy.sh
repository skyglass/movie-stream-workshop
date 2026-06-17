#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
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
: "${APP_NAMESPACE:=movie-stream}"
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

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' is not installed or not on PATH." >&2
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

cleanup_kubernetes() {
  require_cmd aws
  require_cmd kubectl

  local cluster_name
  cluster_name="$(terraform -chdir="$INFRA_DIR" output -raw cluster_name 2>/dev/null || true)"
  if [[ -z "$cluster_name" ]]; then
    cluster_name="${INFRA_NAME_PREFIX}-eks"
  fi

  if ! aws eks describe-cluster --region "$AWS_REGION" --name "$cluster_name" >/dev/null 2>&1; then
    echo "EKS cluster $cluster_name is not reachable; skipping Kubernetes cleanup."
    return
  fi

  aws eks update-kubeconfig --region "$AWS_REGION" --name "$cluster_name"

  if ! kubectl get namespace "$APP_NAMESPACE" >/dev/null 2>&1; then
    echo "Namespace $APP_NAMESPACE does not exist; skipping Kubernetes cleanup."
    return
  fi

  echo "Deleting application ingress so AWS load balancers are removed before Terraform destroy."
  kubectl -n "$APP_NAMESPACE" delete ingress --all --ignore-not-found=true --wait=false

  for _ in {1..60}; do
    local ingress_count
    ingress_count="$(kubectl -n "$APP_NAMESPACE" get ingress --no-headers 2>/dev/null | wc -l | tr -d ' ')"
    if [[ "$ingress_count" == "0" ]]; then
      break
    fi
    sleep 10
  done

  echo "Deleting namespace and static PV objects. EBS volumes are not deleted."
  kubectl delete namespace "$APP_NAMESPACE" --ignore-not-found=true --wait=false
  kubectl delete pv movies-postgres-ebs-pv keycloak-postgres-ebs-pv --ignore-not-found=true --wait=false
}

destroy_infra() {
  require_cmd terraform
  configure_terraform_vars
  terraform -chdir="$INFRA_DIR" init
  terraform -chdir="$INFRA_DIR" destroy -auto-approve
}

cleanup_kubernetes
destroy_infra

echo "Terraform infrastructure destroyed. The external EBS volumes were not managed by Terraform and remain available for future deployments."
