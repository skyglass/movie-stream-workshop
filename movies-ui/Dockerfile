# ---------- Build stage ----------
FROM node:20-alpine AS build
WORKDIR /app

# Install deps (cached)
COPY package*.json ./
RUN npm ci

# Copy sources & build
COPY . .
# If you need a specific base-href, pass: -- --base-href=/yourpath/
RUN npm run build

# ---------- Serve stage ----------
FROM nginx:alpine
ARG OMDB_API_KEY
ARG MOVIES_PER_PAGE
ARG SEARCH_RESULTS_PER_PAGE
RUN sh -c 'test -n "$OMDB_API_KEY" || { echo "OMDB_API_KEY build argument is required"; exit 1; }; test -n "$MOVIES_PER_PAGE" || { echo "MOVIES_PER_PAGE build argument is required"; exit 1; }; test -n "$SEARCH_RESULTS_PER_PAGE" || { echo "SEARCH_RESULTS_PER_PAGE build argument is required"; exit 1; }'
# Nginx config for SPA routing
COPY ops/nginx.conf /etc/nginx/conf.d/default.conf

# Copy the Angular build output
COPY --from=build /app/dist/movies-ui/browser /usr/share/nginx/html

# Bake the OMDb key into the static runtime config for this local workshop image.
COPY ops/app-config.default.json /tmp/app-config.default.json
RUN sh -c 'sed -e "s|__OMDB_API_KEY__|$OMDB_API_KEY|g" -e "s|__MOVIES_PER_PAGE__|$MOVIES_PER_PAGE|g" -e "s|__SEARCH_RESULTS_PER_PAGE__|$SEARCH_RESULTS_PER_PAGE|g" /tmp/app-config.default.json > /usr/share/nginx/html/app-config.json'
