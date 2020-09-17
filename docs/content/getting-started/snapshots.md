# Snapshots

If you want to use a SNAPSHOT add the Sonatype OSS SNAPSHOT repository to your `pom.xml`:
```xml
<repositories>
    <repository>
        <id>oss.sonatype.org-snapshot</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

And add a drasyl SNAPSHOT version as dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>org.drasyl</groupId>
    <artifactId>drasyl-core</artifactId>
    <version>0.1.3-SNAPSHOT</version>
</dependency>
```