# Development

## Release 

### 1. Ensure Changelog is up-to-date 

[CHANGELOG](CHANGELOG.md)

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
# drasyl-cli/target/drasyl-*.zip generated
```

## Build and Push Docker Image

```bash
docker build -t git.informatik.uni-hamburg.de:4567/sane-public/drasyl:latest .
docker push git.informatik.uni-hamburg.de:4567/sane-public/drasyl:latest
```

## Code Style

We use a custom Java code style for which is described in the following file [.editorconfig](../../.editorconfig).