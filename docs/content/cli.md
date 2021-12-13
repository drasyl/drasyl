# Command Line Tool

It is possible to run drasyl from the command line using the `drasyl` command.
The `drasyl` command makes it possible to start up drasyl nodes, generate identities, and more directly from the command line.

You can download the `drasyl` command from [GitHub](https://github.com/drasyl-overlay/drasyl/releases).
The download includes a `bin/drasyl` executable for Linux/macOS users and a `bin/drasyl.bat` for Windows users.

Run `drasyl help` to get an overview of available commands and flags:

```bash
$ drasyl help
drasyl Command Line Interface: A collection of utilities for drasyl.

Usage: drasyl [COMMAND]

  genidentity  Generates and outputs a new identity
  help         Displays help information about the specified command
  node         Runs a drasyl node
  perf         Tool for measuring network performance
  pubkey       Dervices the public key and prints it to standard output from a
                 private key given on standard input
  tunnel       Expose safely local networked services behind through NATs and
                 firewalls to other computers
  version      Shows the drasyl version number, the java version, and the
                 architecture
  wormhole     Transfer a text message or file from one computer to another,
                 safely and through NATs and firewalls

The environment variable JAVA_OPTS can be used to pass options to the JVM.
```

## Docker

The [`drasyl/drasyl`](https://hub.docker.com/r/drasyl/drasyl) image provides the `drasyl` command to the host. So no need to install drasyl on your machine, you can just use this docker image.

For instance:

```bash
$ docker run -i -t drasyl/drasyl version
- drasyl-cli.version 0.6.0 (01183ed)
- drasyl-core.version 0.6.0 (01183ed)
- drasyl-node.version 0.6.0 (01183ed)
- drasyl-plugin-groups-client.version 0.6.0 (01183ed)
- drasyl-plugin-groups-manager.version 0.6.0 (01183ed)
- java.version 17.0.1
- os.name Mac OS X
- os.version 11.4
- os.arch x86_64
```

To run a node:
```bash
# generate an identity (this can take some time)
$ docker run -i -t drasyl/drasyl genidentity | grep -v "WARNING:" > drasyl.identity

# start a node
$ docker run -i -t -p 22527:22527 \
    -v $PWD/drasyl.identity:/drasyl.identity \
    drasyl/drasyl node
```

This command passes the just generated identity to the docker container and then launch the `drasyl node` command.

## Homebrew

The `drasyl` command can also be downloaded with [Homebrew](https://brew.sh/):

```bash
$ brew install drasyl-overlay/tap/drasyl
```

## Chocolatey

The `drasyl` command can also be downloaded with [Chocolatey](https://chocolatey.org/packages/drasyl):

```bash
$ choco install drasyl
```
