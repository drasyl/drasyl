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
    environment:
      CONFIG_FORCE_drasyl_UID: $DRASYL_UID
      CONFIG_FORCE_drasyl_monitoring_enabled: "true"
      CONFIG_FORCE_drasyl_monitoring_token: $MONITORING_TOKEN
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
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}.service=${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}@docker"

      - "traefik.http.services.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.loadbalancer.server.port=8080"
      - "traefik.http.middlewares.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.stripprefix.prefixes=/monitoring"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.rule=Host(\`${APP_DEPLOY_HOST}\`) && PathPrefix(\`/monitoring\`)"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.entrypoints=websecure"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.tls.certresolver=mytlschallenge"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.service=${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring@docker"
      - "traefik.http.routers.${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring.middlewares=${CI_PROJECT_NAME}_${CI_COMMIT_REF_SLUG}_monitoring@docker"


EOF
