# Development

## Release new version

```bash
rm -f release.properties
mvn clean -DskipTests -Darguments=-DskipTests release:prepare
```

**An additional call of `mvn release:perform` is not necessary!**

Wait for GitLab CI to finish build tasks and select "Keep" on job artifact.

Add Release Notes to git Tag on [GitLab](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/tags).

Add Asset to Release 

```bash
curl --request POST \
     --header "PRIVATE-TOKEN: s3cr3tPassw0rd" \
     --data name="drasyl-1.2.zip" \
     --data url="https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/jobs/artifacts/1.2/raw/drasyl-1.2.zip?job=maven-deploy" \
     "https://git.informatik.uni-hamburg.de/api/v4/projects/3070/releases/1.2/assets/links"
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

We use a custom Java code style for which is described in the following file (IntelliJ IDEA only) [SANE.xml](../SANE.xml).