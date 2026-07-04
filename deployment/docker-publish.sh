#!/usr/bin/env bash
if [ -z "${BASH_VERSION:-}" ]; then
  exec bash "$0" "$@"
fi

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env.prod}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Create it from deployment/.env.prod.example first." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

: "${IMAGE_VERSION:=1.2.0-movie-challenge}"
: "${DOCKER_SERVER:=ghcr.io}"

required_vars=(
  CONTAINER_REGISTRY
  GITHUB_USERNAME
  GITHUB_TOKEN
  OMDB_API_KEY
  MOVIES_PER_PAGE
  SEARCH_RESULTS_PER_PAGE
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "Required variable $var is missing in $ENV_FILE." >&2
    exit 1
  fi
done

CONTAINER_REGISTRY="${CONTAINER_REGISTRY%/}"

echo "$GITHUB_TOKEN" | docker login "$DOCKER_SERVER" -u "$GITHUB_USERNAME" --password-stdin

build_and_push() {
  local service="$1"
  local context="$2"
  local dockerfile="$3"
  shift 3

  local image="${CONTAINER_REGISTRY}/${service}:${IMAGE_VERSION}"

  echo "Building $image"
  if [[ -n "${DOCKER_PLATFORM:-}" ]]; then
    docker build --platform "$DOCKER_PLATFORM" -f "$dockerfile" -t "$image" "$@" "$context"
  else
    docker build -f "$dockerfile" -t "$image" "$@" "$context"
  fi

  echo "Pushing $image"
  docker push "$image"
}

build_and_push "movie-gateway" "$ROOT_DIR" "$ROOT_DIR/movie-gateway/Dockerfile"
build_and_push "movies-api" "$ROOT_DIR" "$ROOT_DIR/movies-api/Dockerfile"
build_and_push "movies-ui" "$ROOT_DIR/movies-ui" "$ROOT_DIR/movies-ui/Dockerfile" --build-arg "OMDB_API_KEY=$OMDB_API_KEY" --build-arg "MOVIES_PER_PAGE=$MOVIES_PER_PAGE" --build-arg "SEARCH_RESULTS_PER_PAGE=$SEARCH_RESULTS_PER_PAGE"

echo "Published movie-gateway, movies-api, and movies-ui with version $IMAGE_VERSION."
