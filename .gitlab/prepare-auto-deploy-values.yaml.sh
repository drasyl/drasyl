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
podArgs:
  - node
  - --verbose
  - TRACE

podEnv:
  - name: CONFIG_FORCE_drasyl_network_id
    value: "${DRASYL_NETWORK_ID}"
  - name: CONFIG_FORCE_drasyl_identity_proof__of__work
    value: "${DRASYL_PROOF_OF_WORK:-0}"
  - name: CONFIG_FORCE_drasyl_identity_public__key
    value: "$DRASYL_PUBLIC_KEY"
  - name: CONFIG_FORCE_drasyl_identity_private__key
    value: "$DRASYL_PRIVATE_KEY"
  - name: CONFIG_FORCE_drasyl_remote_bind__port
    value: "22527"
  - name: CONFIG_FORCE_drasyl_remote_expose_enabled
    value: "false"
  - name: CONFIG_FORCE_drasyl_remote_super__peer_enabled
    value: "false"
  - name: CONFIG_FORCE_drasyl_monitoring_enabled
    value: "true"
  - name: CONFIG_FORCE_drasyl_monitoring_host__tag
    value: "$(echo $CI_ENVIRONMENT_URL | awk -F[/:] '{print $4}')"
  - name: CONFIG_FORCE_drasyl_monitoring_influx_uri
    value: https://influxdb.incorum.org
  - name: CONFIG_FORCE_drasyl_monitoring_influx_user
    value: drasyl
  - name: CONFIG_FORCE_drasyl_monitoring_influx_password
    value: "$DRASYL_MONITORING_PASSWORD"
  - name: CONFIG_FORCE_drasyl_monitoring_influx_reporting__frequency
    value: 10s
  - name: JAVA_OPTS
    value: "$JAVA_OPTS"

port: $PORT
EOF
