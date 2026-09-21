# Build context is frontend/pulseops-web.
#   docker build -f frontend/pulseops-web/Dockerfile -t pulseops-web frontend/pulseops-web

FROM node:22-alpine AS build
WORKDIR /build

# npm ci needs the lockfile and installs exactly what it pins.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine AS runtime

# The nginx image renders templates/ with envsubst at start-up, so the API upstream is a
# deploy-time setting rather than a baked-in one.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /build/dist/pulseops-web/browser /usr/share/nginx/html

ENV API_UPSTREAM=http://api:8081

EXPOSE 8080
