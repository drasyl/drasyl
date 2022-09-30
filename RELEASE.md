# Release

This file describes how to make the various kinds of releases.

## Making a release

* Verify that all [Github Action workflows](https://github.com/drasyl-overlay/drasyl/actions) succeed on master.
* Update version
  in [swagger.json](drasyl-plugin-groups-manager/src/main/resources/public/swagger.json).
* Ensure [CHANGELOG](CHANGELOG.md) is up-to-date (e.g. version and release date is set).
* Commit changes.
* Build software and push to maven repository:
```bash
rm -f release.properties
mvn clean release:prepare
```

An additional call of `mvn release:perform` is not necessary! A GitHub Action workflow performs this
tasks automatically.

* Wait for the GitHub Action to deploy new version to Maven Central ([Deploy](https://github.com/drasyl-overlay/drasyl/actions/workflows/deploy.yml) workflow).
* Deploy to our public super peers (this is a manual process).
* Create Release on GitHub:
  * Go to https://github.com/drasyl-overlay/drasyl/tags.
  * Click `Create release` for tag `v1.2.0`.
  * **Title:** `v1.2.0`
  * **Description:** `[CHANGELOG.md](CHANGELOG.md)`
* Wait for GitHub Action to complete [Release](https://github.com/drasyl-overlay/drasyl/actions/workflows/release.yml) workflow.
* Update back to next SNAPSHOT version
  in [swagger.json](drasyl-plugin-groups-manager/src/main/resources/public/swagger.json)
  and [Chart.yaml](chart/Chart.yaml) and [getting-started.md](docs/content/getting-started.md).
* Push the new version to chocolatey. For instructions see this repo: [https://github.com/drasyl-overlay/drasyl-choco](https://github.com/drasyl-overlay/drasyl-choco/blob/master/RELEASE.md)
* Create/update version branch (e.g., `v1.2` if you release `v1.2.0`) and push.

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
