FROM container-registry.oracle.com/os/oraclelinux:7-slim AS build-dependencies

RUN yum update -y \
    && yum install -y unzip

FROM ghcr.io/graalvm/jdk:java11 AS build

ADD . /build

COPY --from=build-dependencies /bin/unzip /bin/

RUN cd /build && \
    ./mvnw --quiet --projects drasyl-jtasklet --also-make -Pfast -DskipTests -Dmaven.javadoc.skip=true package && \
    unzip -qq ./jtasklet-*.zip -d /

FROM ghcr.io/graalvm/graalvm-ce:ol9-java11-22.3.1

RUN gu install js

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
            <pattern>%d{"yyyy-MM-dd'"'T'"'HH:mm:ss,SSSXXX"} %-5level --- [%12.12thread] %-40.40logger{40} : %msg%n</pattern>\n\
        </encoder>\n\
    </appender>\n\
\n\
    <logger name="io.netty" level="warn">\n\
    </logger>\n\
\n\
    <logger name="org.drasyl.jtasklet" level="DEBUG">\n\
    </logger>\n\
\n\
    <root level="warn">\n\
        <appender-ref ref="Console"/>\n\
    </root>\n\
</configuration>' >> /usr/local/share/jtasklet/logback.xml

COPY jtasklet.sh /usr/bin/jtasklet.sh

RUN chmod +x /usr/bin/jtasklet.sh

# Set user and group
ARG user=appuser
ARG group=appuser
ARG uid=1000
ARG gid=1000
RUN groupadd -g ${gid} ${group}
RUN useradd -u ${uid} -g ${group} -s /bin/sh -m ${user}

# Switch to user
USER ${uid}:${gid}

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /jtasklet/

ENV JAVA_SCC_OPTS ""
ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/jtasklet/logback.xml ${JAVA_SCC_OPTS}"

ENTRYPOINT ["jtasklet.sh"]