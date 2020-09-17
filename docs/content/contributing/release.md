# Release 

### 1. Ensure Changelog is up-to-date 

[CHANGELOG](https://github.com/drasyl-overlay/drasyl/blob/master/CHANGELOG.md)

### 2. Bump Maven Version

```bash
rm -f release.properties
mvn clean release:prepare
```

**An additional call of `mvn release:perform` is not necessary!**

### 3. Preservate Builds

Wait for GitLab CI to finish build tasks and select "Keep" on maven-deploy job artifact.

### 4. Deploy new release to production Environment

Start the manual `production` CI job at the pipeline with the release in the `master` branch.

### 5. Release to Maven Central Repository

Login into the [Sonatype OSSRH Nexus Repository Manager](https://oss.sonatype.org), go to the "Staging Repositories" tab, and close the corresponding release. Wait for the checks, then refresh and click "Release".

### 6. Create Release on GitHub

Go to [https://github.com/drasyl-overlay/drasyl/releases](https://github.com/drasyl-overlay/drasyl/releases).

**Tag:** `v1.2.0`

**Title:** `v1.2.0`

**Description:** `[CHANGELOG.md](CHANGELOG.md)`

Attach `drasyl-1.2.0.zip` generated from GitLab CI.

### 7. Release Container Image on Docker Hub

```
docker pull git.informatik.uni-hamburg.de:4567/sane-public/drasyl:v1.2.0
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:v1.2.0 drasyl/drasyl:1.2.0
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:v1.2.0 drasyl/drasyl:1.2
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:v1.2.0 drasyl/drasyl:1
docker tag git.informatik.uni-hamburg.de:4567/sane-public/drasyl:v1.2.0 drasyl/drasyl:latest
docker push drasyl/drasyl:1.2.0
docker push drasyl/drasyl:1.2
docker push drasyl/drasyl:1
docker push drasyl/drasyl:latest
```


