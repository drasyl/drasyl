#!/bin/sh
if [[ -z "$CI_COMMIT_TAG" ]]; then
  image_repository=${CI_APPLICATION_REPOSITORY:-$CI_REGISTRY_IMAGE/$CI_COMMIT_REF_SLUG}
  image_tag=${CI_APPLICATION_TAG:-$CI_COMMIT_SHA}
else
  image_repository=${CI_APPLICATION_REPOSITORY:-$CI_REGISTRY_IMAGE}
  image_tag=${CI_APPLICATION_TAG:-$CI_COMMIT_TAG}
fi

host=$CI_ENVIRONMENT_SLUG.$APP_HOST

cat << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: "${CI_PROJECT_NAME}-deployment"
  annotations:
    app.gitlab.com/app: "$CI_PROJECT_PATH_SLUG"
    app.gitlab.com/env: "$CI_ENVIRONMENT_SLUG"
spec:
  selector:
    matchLabels:
      app: "$CI_PROJECT_NAME"
  replicas: 1
  template:
    metadata:
      labels:
        app: drasyl
      annotations:
        app.gitlab.com/app: "$CI_PROJECT_PATH_SLUG"
        app.gitlab.com/env: "$CI_ENVIRONMENT_SLUG"
    spec:
      containers:
      - name: "$CI_PROJECT_NAME"
        image: "${image_repository}:${image_tag}"
        args: ["node"]
        env:
        - name: CONFIG_FORCE_drasyl_loglevel
          value: "TRACE"
        - name: CONFIG_FORCE_drasyl_identity_proof__of__work
          value: "${DRASYL_PROOF_OF_WORK:-0}"
        - name: CONFIG_FORCE_drasyl_identity_public__key
          value: "$DRASYL_PUBLIC_KEY"
        - name: CONFIG_FORCE_drasyl_identity_private__key
          value: "$DRASYL_PRIVATE_KEY"
        - name: CONFIG_FORCE_drasyl_server_endpoints_0
          value: "wss://$host"
        - name: CONFIG_FORCE_drasyl_super__peer_enabled
          value: "false"
        - name: CONFIG_FORCE_drasyl_monitoring_enabled
          value: "true"
        - name: CONFIG_FORCE_drasyl_monitoring_influx_uri
          value: https://influxdb.incorum.org
        - name: CONFIG_FORCE_drasyl_monitoring_influx_user
          value: drasyl
        - name: CONFIG_FORCE_drasyl_monitoring_influx_password
          value: "$DRASYL_MONITORING_PASSWORD"
        - name: CONFIG_FORCE_drasyl_monitoring_influx_reporting__frequency
          value: 10s
        - name: SENTRY_DNS
          value: "$SENTRY_DNS"
        - name: SENTRY_ENVIRONMENT
          value: "$CI_ENVIRONMENT_NAME"
        - name: JAVA_OPTS
          value: "$JAVA_OPTS"
---
apiVersion: v1
kind: Service
metadata:
  name: "${CI_PROJECT_NAME}-service"
  labels:
    app: "$CI_PROJECT_NAME"
spec:
  selector:
    app: "$CI_PROJECT_NAME"
  ports:
  - port: 22527
    targetPort: 22527
---
apiVersion: extensions/v1beta1
kind: Ingress
metadata:
  name: "${CI_PROJECT_NAME}-ingress"
  labels:
    app: drasyl
  annotations:
    kubernetes.io/ingress.class: "nginx"
    kubernetes.io/tls-acme: 'true'
spec:
  tls:
    - hosts:
        - "$host"
      secretName: "${CI_PROJECT_NAME}-secret-tls"
  rules:
  - host: "$host"
    http:
      paths:
      - path: /
        backend:
          serviceName: "${CI_PROJECT_NAME}-service"
          servicePort: 22527
EOF
