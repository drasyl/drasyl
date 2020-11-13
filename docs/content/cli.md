# Command Line Tool

It is possible to run drasyl from the command line using the `drasyl` command.
The `drasyl` command makes it possible to start up drasyl nodes, generate identities, and more directly from the command line.

You can download the `drasyl` command from [GitHub](https://github.com/drasyl-overlay/drasyl/releases).
The download includes a `bin/drasyl` executable for Linux/macOS users and a `bin/drasyl.bat` for Windows users.

Run `drasyl help` to get an overview of available commands and flags:

```bash
$ drasyl help
Usage:
  drasyl [flags]
  drasyl [command]

Available Commands:
help           Show help for drasyl commands and flags.
node           Run a drasyl node.
genidentity    Generate and output new Identity.
wormhole       Transfer a text message from one node to another, safely.
version        Show the version number.

Flags:
-c,--config <file>        Load configuration from specified file (default:
                          /Users/heiko/drasyl.conf).
-h,--help                 Show this help.
-v,--verbose <level>      Sets the log level (off, error, warn, info, debug, trace; default: warn)

Use "drasyl [command] --help" for more information about a command.
```

## Docker

The [`drasyl/drasyl`](https://hub.docker.com/r/drasyl/drasyl) image provides the `drasyl` command to the host. So no need to install drasyl on your machine, you can just use this docker image.

For instance:

```bash
$ docker run -i -t drasyl/drasyl version
drasyl v0.2.0 (ef906c1)
- os.name Linux
- os.version 4.19.76-linuxkit
- os.arch amd64
- java.version 11.0.8
```

To run a node:
```bash
# generate an identity (this can take some time)
$ docker run -i -t drasyl/drasyl genidentity | grep -v "WARNING:" > drasyl.identity.json

# start a node
$ docker run -i -t -p 22527:22527 \
    -v $PWD/drasyl.identity.json:/drasyl.identity.json \
    drasyl/drasyl node
```

This command passes the just generated identity to the docker container and then launch the `drasyl node` command.

## Homebrew

The `drasyl` command can also be downloaded with [Homebrew](https://brew.sh/):

```bash
$ brew install drasyl-overlay/drasyl/drasyl
```