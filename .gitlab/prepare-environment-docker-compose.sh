#!/bin/sh
#
# Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
# IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
# DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
# OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
# OR OTHER DEALINGS IN THE SOFTWARE.
#
if [[ -z "$DRASYL_NETWORK_ID" ]]; then
  DRASYL_NETWORK_ID=$((RANDOM - 32768))
fi

if [[ -z "$PORT" ]]; then
  PORT=$((RANDOM + 32768))
fi

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
    container_name: "${CI_PROJECT_NAME}-${CI_PROJECT_ID}-${CI_ENVIRONMENT_SLUG}"
    hostname: ${APP_DEPLOY_HOST}
    command: node --verbose DEBUG
    environment:
      CONFIG_FORCE_drasyl_network_id: "$DRASYL_NETWORK_ID"
      CONFIG_FORCE_drasyl_identity_proof__of__work: $DRASYL_PROOF_OF_WORK
      CONFIG_FORCE_drasyl_identity_public__key: $DRASYL_PUBLIC_KEY
      CONFIG_FORCE_drasyl_identity_private__key: $DRASYL_PRIVATE_KEY
      CONFIG_FORCE_drasyl_remote_endpoints_0: udp://${APP_DEPLOY_HOST}:${PORT}?publicKey=${DRASYL_PUBLIC_KEY}&networkId=${DRASYL_NETWORK_ID}
      CONFIG_FORCE_drasyl_remote_expose_enabled: "false"
      CONFIG_FORCE_drasyl_remote_super__peer_enabled: "false"
      CONFIG_FORCE_drasyl_monitoring_enabled: "true"
      CONFIG_FORCE_drasyl_monitoring_influx_uri: https://influxdb.incorum.org
      CONFIG_FORCE_drasyl_monitoring_influx_user: drasyl
      CONFIG_FORCE_drasyl_monitoring_influx_password: $DRASYL_MONITORING_PASSWORD
      CONFIG_FORCE_drasyl_monitoring_influx_reporting__frequency: 10s
      JAVA_OPTS: ${JAVA_OPTS}
    ports:
      - "${PORT}:22527/udp"
    networks:
      - default
      - proxy
EOF
