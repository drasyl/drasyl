FROM kubeless/unzip AS build

ADD ./drasyl-*.zip .

RUN unzip -qq ./drasyl-*.zip && \
    rm ./drasyl-*.zip

FROM openjdk:11-jre-slim

RUN mkdir /usr/local/share/drasyl && \
    ln -s ../share/drasyl/bin/drasyl /usr/local/bin/drasyl

COPY --from=build ./drasyl-* /usr/local/share/drasyl/

# http
EXPOSE 8080

ENTRYPOINT ["drasyl"]
