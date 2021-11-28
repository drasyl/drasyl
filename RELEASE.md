# Release

This file describes how to make the various kinds of releases.

## Making a release

* Update  version
  in [swagger.json](drasyl-plugin-groups-manager/src/main/resources/public/swagger.json)
  and [Chart.yaml](chart/Chart.yaml) and [getting-started.md](docs/content/getting-started.md).
* Ensure [CHANGELOG](CHANGELOG.md) is up-to-date (e.g. version and release date is set).
* Build software and push to maven repository:
```bash
rm -f release.properties
mvn clean release:prepare
```
An additional call of `mvn release:perform` is not necessary! GitLab CI performs this tasks automatically.

* Wait for the GitHub Action to deploy new version to Maven Central ("Deploy" workflow).
* Deploy to our public super peers (this is a manual process).
* Create Release on GitHub:
  * Go to https://github.com/drasyl-overlay/drasyl/tags.
  * Click `Create release` for tag `v1.2.0`.
  * **Title:** `v1.2.0`
  * **Description:** `[CHANGELOG.md](CHANGELOG.md)`
* Wait for GitHub Action to complete "Release" workflow.
* Update back to next SNAPSHOT version
  in [swagger.json](drasyl-plugin-groups-manager/src/main/resources/public/swagger.json)
  and [Chart.yaml](chart/Chart.yaml) and [getting-started.md](docs/content/getting-started.md).
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
