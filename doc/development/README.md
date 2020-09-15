# Development

## Release 

### 1. Ensure Changelog is up-to-date 

[CHANGELOG](../../CHANGELOG.md)

### 2. Bump Maven Version

```bash
rm -f release.properties
mvn clean release:prepare
```

**An additional call of `mvn release:perform` is not necessary!**

### 3. Preservate Builds

Wait for GitLab CI to finish build tasks and select "Keep" on maven-deploy job artifact.

### 4. Create Release on GitHub

Go to https://github.com/drasyl-overlay/drasyl/releases.

**Tag:** `v1.2.0`

**Title:** `v1.2.0`

**Description:** `[CHANGELOG.md](CHANGELOG.md)`

Attach `drasyl-1.2.0.zip` generated from GitLab CI.

### 5. Release Container Image on Docker Hub

```
docker pull docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:1-2-0
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:1-2-0 drasy/drasyl:1.2.0
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:1-2-0 drasy/drasyl:1.2
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:1-2-0 drasy/drasyl:1
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:1-2-0 drasy/drasyl:latest
docker push drasy/drasyl:1.2.0
docker push drasy/drasyl:1.2
docker push drasyl:1
docker push drasyl:latest
```

## Build dist

```bash
mvn -DskipTests -pl drasyl-cli -am package
# drasyl-*.zip generated
```

## Build and Push Docker Image

```bash
docker build -t drasyl/drasyl:latest .
docker push drasyl/drasyl:latest
```

## Available Super Peers

| **Endpoint**                      | **Public Key**                                                       | **Used drasyl version**                                                    |
|-----------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------------------|
| `wss://production.env.drasyl.org` | `025fff6f625f5dee816d9f8fe43895479aecfda187cb6a3330894a07e698bc5bd8` | Latest stable [release](https://github.com/drasyl-overlay/drasyl/releases) |
| `wss://staging.env.drasyl.org`    | `03096ae3080a369829a44847d5af1f652bef3f9921e9e1bbad64970babe6d3c502` | Latest [successful master branch build](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines?page=1&scope=all&ref=master&status=success)                                      |

## Use latest snapshot version

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
