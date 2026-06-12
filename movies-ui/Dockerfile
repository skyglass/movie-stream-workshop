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
RUN sh -c 'test -n "$OMDB_API_KEY" || { echo "OMDB_API_KEY build argument is required"; exit 1; }'
# Nginx config for SPA routing
COPY ops/nginx.conf /etc/nginx/conf.d/default.conf

# Copy the Angular build output
COPY --from=build /app/dist/movies-ui/browser /usr/share/nginx/html

# Bake the OMDb key into the static runtime config for this local workshop image.
COPY ops/app-config.default.json /tmp/app-config.default.json
RUN sh -c 'sed "s|__OMDB_API_KEY__|$OMDB_API_KEY|g" /tmp/app-config.default.json > /usr/share/nginx/html/app-config.json'
