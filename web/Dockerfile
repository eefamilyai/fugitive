# Fugitive web client — static build served by nginx.
# The shared Kotlin engine is pre-compiled into src/engine (./gradlew :core:engine:syncWebEngine),
# so this image needs only Node to build.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Runtime config: mount your own config.json over this file (see README).
COPY public/config.example.json /usr/share/nginx/html/config.json
EXPOSE 80
HEALTHCHECK CMD wget -qO- http://127.0.0.1/healthz || exit 1
