# Logging

To use logging, you must configure it via the [SLF4J](http://www.slf4j.org/) backend, such as
[Logback](http://logback.qos.ch/):

## Add Dependency

Maven:
```xml
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.2.3</version>
</dependency>
```

Other dependency managers:
```java
Gradle : compile "ch.qos.logback:logback-classic:1.2.3" // build.gradle 
   Ivy : <dependency org="ch.qos.logback" name="logback-classic" rev="1.2.3" conf="build" /> // ivy.xml
   SBT : libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3" // build.sbt
```

## Example Configuration
```xml
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- The errors sent by this library can/should be ignored... -->
  <logger name="com.offbynull.portmapper" level="off" />

  <root level="debug">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
```

!!! important "Exceptions in `com.offbynull.portmapper` package"

    To enable port forwarding on NAT-enabled routers, drasyl uses the
    [Port Mapper](https://github.com/offbynull/portmapper) library. Sadly, it is "normal behavior"
    for this library to throw exceptions during operation and log them. This is annoying, but does
    not indicate a problem or error and should therefore be ignored by setting the log-level to
    `OFF` for packages in `com.offbynull.portmapper` (see example configuration above).