# syntax=docker/dockerfile:1

ARG BUILDPLATFORM
ARG TARGETPLATFORM

FROM --platform=$BUILDPLATFORM ghcr.io/graalvm/jdk:java11 AS build

WORKDIR /build
ADD . /build

RUN microdnf install -y unzip || yum install -y unzip

RUN ./mvnw --quiet \
    --projects drasyl-jtasklet \
    --also-make \
    -Pfast \
    -DskipTests \
    -Dmaven.javadoc.skip=true \
    package \
 && unzip -qq ./jtasklet-*.zip -d /

FROM ghcr.io/graalvm/graalvm-ce:ol9-java11-22.3.1

RUN gu install js

RUN mkdir -p /usr/local/share/jtasklet \
    && ln -s ../share/jtasklet/bin/jtasklet /usr/local/bin/jtasklet

COPY --from=build /jtasklet-* /usr/local/share/jtasklet/
ADD ./tasks/ /tasks/

# use logback.xml without timestamps
RUN cat > /usr/local/share/jtasklet/logback.xml <<'EOF'
<configuration>
    <statusListener class="ch.qos.logback.core.status.NopStatusListener"/>

    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{"yyyy-MM-dd'T'HH:mm:ss,SSSXXX"} %-5level --- [%12.12thread] %-40.40logger{40} : %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="io.netty" level="warn">
    </logger>

    <logger name="org.drasyl.jtasklet" level="DEBUG">
    </logger>

    <root level="warn">
        <appender-ref ref="Console"/>
    </root>
</configuration>
EOF

COPY jtasklet.sh /usr/bin/jtasklet.sh
RUN chmod +x /usr/bin/jtasklet.sh

ARG user=appuser
ARG group=appuser
ARG uid=1000
ARG gid=1000

RUN groupadd -g ${gid} ${group} \
 && useradd -u ${uid} -g ${group} -s /bin/sh -m ${user}

USER ${uid}:${gid}

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /jtasklet/

ENV JAVA_SCC_OPTS=""
ENV JAVA_OPTS="-Dlogback.configurationFile=/usr/local/share/jtasklet/logback.xml ${JAVA_SCC_OPTS}"

ENTRYPOINT ["jtasklet.sh"]
