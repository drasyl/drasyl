#!/bin/sh
#
# Copyright (c) 2020.
#
# This file is part of drasyl.
#
#  drasyl is free software: you can redistribute it and/or modify
#  it under the terms of the GNU Lesser General Public License as published by
#  the Free Software Foundation, either version 3 of the License, or
#  (at your option) any later version.
#
#  drasyl is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU Lesser General Public License for more details.
#
#  You should have received a copy of the GNU Lesser General Public License
#  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
#

if [[ -z "$CI_COMMIT_TAG" ]]; then
  image_repository=${CI_APPLICATION_REPOSITORY:-$CI_REGISTRY_IMAGE/$CI_COMMIT_REF_SLUG}
  image_tag=${CI_APPLICATION_TAG:-$CI_COMMIT_SHA}
else
  image_repository=${CI_APPLICATION_REPOSITORY:-$CI_REGISTRY_IMAGE}
  image_tag=${CI_APPLICATION_TAG:-$CI_COMMIT_TAG}
fi

if [[ -z "$DRASYL_NETWORK_ID" ]]; then
  DRASYL_NETWORK_ID=$((RANDOM - 32768))
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
        args: ["node", "--verbose", "TRACE"]
        env:
        - name: CONFIG_FORCE_drasyl_network_id
          value: "${DRASYL_NETWORK_ID}"
        - name: CONFIG_FORCE_drasyl_identity_proof__of__work
          value: "${DRASYL_PROOF_OF_WORK:-0}"
        - name: CONFIG_FORCE_drasyl_identity_public__key
          value: "$DRASYL_PUBLIC_KEY"
        - name: CONFIG_FORCE_drasyl_identity_private__key
          value: "$DRASYL_PRIVATE_KEY"
        - name: CONFIG_FORCE_drasyl_server_endpoints_0
          value: "wss://$host"
        - name: CONFIG_FORCE_drasyl_server_expose_enabled
          value: "false"
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
