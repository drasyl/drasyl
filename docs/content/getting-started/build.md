# Build

## Build dist

To build the maven dist as `drasyl-*.zip`, run the following command:

```bash
mvn -DskipTests -pl drasyl-cli -am package
```

## Build Docker Image

To build the docker image, run the following command:

```bash
docker build -t drasyl/drasyl:latest .
```