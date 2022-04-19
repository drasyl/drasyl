FROM container-registry.oracle.com/os/oraclelinux:7-slim AS build-dependencies

RUN yum update -y \
    && yum install -y unzip

FROM ghcr.io/graalvm/jdk:java11 AS build

ADD . /build

COPY --from=build-dependencies /bin/unzip /bin/

RUN cd /build && \
    ./mvnw --quiet --projects drasyl-jtasklet --also-make -DskipTests -Dmaven.javadoc.skip=true package && \
    unzip -qq ./jtasklet-*.zip -d /

FROM ghcr.io/graalvm/graalvm-ce:java11

RUN mkdir /usr/local/share/jtasklet && \
    ln -s ../share/jtasklet/bin/jtasklet /usr/local/bin/jtasklet

COPY --from=build /jtasklet-* /usr/local/share/jtasklet/

ADD ./tasks/ /tasks/

# use logback.xml without timestamps
RUN echo '<configuration>\n\
    <statusListener class="ch.qos.logback.core.status.NopStatusListener"/>\n\
\n\
    <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">\n\
        <encoder>\n\
            <pattern>%-5level --- [%12.12thread] %-40.40logger{40} : %msg%n</pattern>\n\
        </encoder>\n\
    </appender>\n\
\n\
    <logger name="io.netty" level="warn">\n\
    </logger>\n\
\n\
    <root level="warn">\n\
        <appender-ref ref="Console"/>\n\
    </root>\n\
</configuration>' >> /usr/local/share/jtasklet/logback.xml

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /jtasklet/

ENV JAVA_SCC_OPTS ""
ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/jtasklet/logback.xml ${JAVA_SCC_OPTS}"

ENTRYPOINT ["jtasklet"]
