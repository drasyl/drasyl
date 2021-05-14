# Release

This file describes how to make the various kinds of releases.

## Making a release

* Remove the nightly/snapshot information from the [getting-started.md](docs/content/getting-started.md) and the [index.md](docs/content/index.md).
* Update version in [README.md](README.md) and [swagger.json](drasyl-plugin-groups-manager/src/main/resources/public/swagger.json)
* Ensure [CHANGELOG](CHANGELOG.md) is up-to-date (e.g. version and release date is set).
* Build software and push to maven repository:
```bash
rm -f release.properties
mvn clean release:prepare
```
An additional call of `mvn release:perform` is not necessary! GitLab CI performs this tasks automatically.**

* Wait for the GitLab CI to complete then.
* Deploy to our public super peers (this is a manual process).
* Release to Maven Central Repository by logging into the [Sonatype OSSRH Nexus Repository Manager](https://oss.sonatype.org), going to the "Staging Repositories" tab, and closing the corresponding release. Wait for the checks, then refresh and click "Release".
* Create Release on GitHub:
  * Go to https://github.com/drasyl-overlay/drasyl/releases.
  * **Tag:** `v1.2.0`
  * **Title:** `v1.2.0`
  * **Description:** `[CHANGELOG.md](CHANGELOG.md)`
* Wait for GitHub Action to complete "Release" workflow.
* Re-add the nightly/snapshot information to the [getting-started.md](docs/content/getting-started.md) and the [index.md](docs/content/index.md).
* Update the Homebrew Formula: https://github.com/drasyl-overlay/homebrew-drasyl/blob/main/Formula/drasyl.rb.
* Push the new version to chocolatey. For instructions see this repo: [https://github.com/drasyl-overlay/drasyl-choco](https://github.com/drasyl-overlay/drasyl-choco/blob/master/RELEASE.md)

## Making a manual build of docker

The drasyl docker image should autobuild on via GitLab CI. If it doesn't or needs to be updated then
rebuild like this.

```
mvn package
docker build -t drasyl/drasyl:1.2.0 -t drasyl/drasyl:1.2 -t drasyl/drasyl:1 -t drasyl/drasyl:latest .
docker push drasyl/drasyl:1.2.0
docker push drasyl/drasyl:1.2
docker push drasyl/drasyl:1
docker push drasyl/drasyl:latest
```
