# Development

## Release new version

Ensure [CHANGELOG](CHANGELOG.md) is up-to-date.

```bash
rm -f release.properties
mvn clean release:prepare
```

**An additional call of `mvn release:perform` is not necessary!**

Wait for GitLab CI to finish build tasks and select "Keep" on maven-deploy job artifact.

Add Release Notes `[CHANGELOG.md](CHANGELOG.md)` to git Tag on [GitLab](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/tags).

Add `https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/jobs/artifacts/0.1.0/raw/drasyl-0.1.0.zip?job=maven-deploy` / `drasyl-0.1.0-zip` release asset.

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