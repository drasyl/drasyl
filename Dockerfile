FROM kubeless/unzip AS build

ADD all/target/drasyl-*.zip ./
#ADD all/monitoring-page drasyl/monitoring-page

RUN unzip -qq './drasyl-*.zip' -d ./drasyl && \
    rm ./drasyl-*.zip

# our final base image
FROM kroeb/slim-jre-11-with-curl:latest

COPY --from=build ./drasyl/drasyl-* /drasyl/
#COPY --from=build drasyl/monitoring-page/dist/ /drasyl/public/

WORKDIR ./drasyl

EXPOSE 22527 8080

CMD ["java", "--illegal-access=permit", "-Dconfig.override_with_env_vars=true", "-jar", "drasyl.jar"]

#HEALTHCHECK --start-period=1m \
#    CMD curl 127.0.0.1:22527 2>&1 \
#        | grep -q 'not a WebSocket handshake request: missing upgrade' && \
#        exit 0 || exit 1
