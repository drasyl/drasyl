FROM openjdk:11-jdk-buster AS build

ADD . /build

RUN cd /build && \
    ./mvnw --quiet --projects drasyl-cli --also-make -DskipTests -Dmaven.javadoc.skip=true package && \
    unzip -qq ./drasyl-*.zip -d /

FROM adoptopenjdk:11-jre-openj9

RUN mkdir /usr/local/share/drasyl && \
    ln -s ../share/drasyl/bin/drasyl /usr/local/bin/drasyl

COPY --from=build /drasyl-* /usr/local/share/drasyl/

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
</configuration>' >> /usr/local/share/drasyl/logback.xml

# run as non-root user
RUN groupadd -g 22527 drasyl && \
    useradd -r -u 22527 -g drasyl -d /drasyl/ drasyl && \
    mkdir /drasyl/ && \
    chown drasyl:drasyl /drasyl/

USER drasyl

# create share class folder for openj9
RUN mkdir /drasyl/shareclasses

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /drasyl/

ENV JAVA_SCC_OPTS "-Xquickstart -XX:+IdleTuningGcOnIdle -Xtune:virtualized -Xshareclasses:name=drasyl_scc,cacheDir=/drasyl/shareclasses -Xscmx50M"
ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/drasyl/logback.xml ${JAVA_SCC_OPTS}"

ENTRYPOINT ["drasyl"]
