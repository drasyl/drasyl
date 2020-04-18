## Usage

### Builds

The current version of the CLI can be downloaded from the following address: https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/jobs/artifacts/master/download?job=build-dist

Show help:
```bash
bin/drasyl -h
```

Start drasyl node
```bash
bin/drasyl
```

##  Docker

Show help:
```bash
docker run --rm -ti sanecity/drasyl -h
```

Execute all WoT scripts from the directory `./wot-scripts`
```bash
docker run --rm -ti -v ./path/to/drasyl.conf:/drasyl/drasyl.conf sanecity/drasyl
```