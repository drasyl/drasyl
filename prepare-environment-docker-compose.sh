#!/bin/sh
cat << EOF
version: '3.4'

networks:
  proxy:
    external:
      name: proxy_default

services:
  $CI_PROJECT_NAME:
    image: $APP_IMAGE
    restart: always
    container_name: $CI_PROJECT_NAME-$CI_PROJECT_ID-$CI_COMMIT_REF_SLUG
    hostname: ${APP_DEPLOY_HOST}
    environment:
      CONFIG_FORCE_drasyl_entry__points_0: wss://${APP_DEPLOY_HOST}
      SENTRY_DNS: ${SENTRY_DNS}
      SENTRY_ENVIRONMENT: ${SENTRY_ENVIRONMENT}
    networks:
      - default
      - proxy
    labels:
      - "traefik.enable=true"
      - "traefik.docker.network=proxy_default"

      - "traefik.http.services.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}.loadbalancer.server.port=22527"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}.rule=Host(\`${APP_DEPLOY_HOST}\`)"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}.entrypoints=websecure"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}.tls.certresolver=mytlschallenge"
EOF
