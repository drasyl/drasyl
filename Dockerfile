FROM eclipse-temurin:11-jdk AS build

COPY . /build

WORKDIR /build/

RUN ./mvnw --quiet --projects drasyl-cli --also-make -DskipTests -Dmaven.javadoc.skip=true package

FROM crazymax/7zip AS unzip

COPY --from=build /build/drasyl-*.zip /

RUN 7za x -y ./drasyl-*.zip && rm drasyl-*.zip

FROM eclipse-temurin:11-jre

RUN mkdir /usr/local/share/drasyl && \
    ln -s ../share/drasyl/bin/drasyl /usr/local/bin/drasyl

COPY --from=unzip /drasyl-* /usr/local/share/drasyl/

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
RUN groupadd --gid 22527 drasyl && \
    useradd --system --uid 22527 --gid drasyl --home-dir /drasyl/ --no-log-init drasyl && \
    mkdir /drasyl/ && \
    chown drasyl:drasyl /drasyl/

USER drasyl

EXPOSE 22527/udp
EXPOSE 443/tcp

WORKDIR /drasyl/

ENV JAVA_OPTS "-Dlogback.configurationFile=/usr/local/share/drasyl/logback.xml"

ENTRYPOINT ["drasyl"]
