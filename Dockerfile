FROM kubeless/unzip AS build

ADD ./drasyl-*.zip .

RUN unzip -qq ./drasyl-*.zip && \
    rm ./drasyl-*.zip

FROM git.informatik.uni-hamburg.de:4567/sane/sane-build-images:jre-11-curl-7.64.0

RUN mkdir /usr/local/share/drasyl && \
    ln -s ../share/drasyl/bin/drasyl /usr/local/bin/drasyl

COPY --from=build ./drasyl-* /usr/local/share/drasyl/

EXPOSE 22527

ENTRYPOINT ["drasyl"]

HEALTHCHECK --start-period=15s \
    CMD curl http://localhost:22527 2>&1 \
        | grep -q 'Not a WebSocket Handshake Request: Missing Upgrade'
