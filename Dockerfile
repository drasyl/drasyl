FROM openjdk:11-jdk-buster AS build

ADD . /build

RUN cd /build && \
    ./mvnw --quiet --projects drasyl-jtasklet --also-make -DskipTests -Dmaven.javadoc.skip=true package && \
    unzip -qq ./jtasklet-*.zip -d /

FROM adoptopenjdk:11-jre-openj9

RUN mkdir /usr/local/share/jtasklet && \
    ln -s ../share/jtasklet/bin/jtasklet /usr/local/bin/jtasklet

COPY --from=build /jtasklet-* /usr/local/share/jtasklet/

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

# run as non-root user
RUN groupadd --gid 22527 jtasklet && \
    useradd --system --uid 22527 --gid jtasklet --home-dir /jtasklet/ --no-log-init jtasklet && \
    mkdir /jtasklet/ && \
    chown jtasklet:jtasklet /jtasklet/

USER jtasklet

# create share class folder for openj9
RUN mkdir /jtasklet/shareclasses

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /jtasklet/

ENV JAVA_SCC_OPTS "-Xquickstart -XX:+IdleTuningGcOnIdle -Xtune:virtualized -Xshareclasses:name=jtasklet_scc,cacheDir=/jtasklet/shareclasses -Xscmx50M"
ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/jtasklet/logback.xml ${JAVA_SCC_OPTS}"

ENTRYPOINT ["jtasklet"]
