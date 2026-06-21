#!/usr/bin/env bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi
case ":${SHELLOPTS:-}:" in
  *:posix:*) exec bash "$0" "$@" ;;
esac

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env.prod}"

usage() {
  cat <<'EOF'
Usage: deployment/publish-and-redeploy.sh [image-version]

Build and push movie-gateway, movies-api, and movies-ui images, then redeploy
the EC2 Docker Compose application with the same IMAGE_VERSION.

If image-version is omitted, IMAGE_VERSION is read from deployment/.env.prod.
If image-version is provided, deployment/.env.prod is not modified.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -gt 1 ]]; then
  usage >&2
  exit 1
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from deployment/.env.prod.example first." >&2
  exit 1
fi

RUN_ENV_FILE="$ENV_FILE"
tmp_dir=""

cleanup() {
  if [[ -n "$tmp_dir" ]]; then
    rm -rf "$tmp_dir"
  fi
}
trap cleanup EXIT

if [[ $# -eq 1 ]]; then
  IMAGE_VERSION_OVERRIDE="$1"
  if [[ ! "$IMAGE_VERSION_OVERRIDE" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
    echo "Invalid Docker image tag: $IMAGE_VERSION_OVERRIDE" >&2
    echo "Use letters, digits, underscore, dot, and dash. The first character must be a letter, digit, or underscore." >&2
    exit 1
  fi

  tmp_dir="$(mktemp -d)"
  chmod 700 "$tmp_dir"
  RUN_ENV_FILE="$tmp_dir/.env.prod"

  awk -v image_version="$IMAGE_VERSION_OVERRIDE" '
    BEGIN { updated = 0 }
    /^IMAGE_VERSION=/ {
      print "IMAGE_VERSION=" image_version
      updated = 1
      next
    }
    { print }
    END {
      if (!updated) {
        print "IMAGE_VERSION=" image_version
      }
    }
  ' "$ENV_FILE" >"$RUN_ENV_FILE"
  chmod 600 "$RUN_ENV_FILE"

  echo "Using IMAGE_VERSION=$IMAGE_VERSION_OVERRIDE for this publish and redeploy run."
else
  echo "Using IMAGE_VERSION from $ENV_FILE."
fi

ENV_FILE="$RUN_ENV_FILE" bash "$SCRIPT_DIR/docker-publish.sh"
ENV_FILE="$RUN_ENV_FILE" bash "$SCRIPT_DIR/start.sh" app

echo "Published images and redeployed the application."
