[<img src="https://docs.drasyl.org/master/assets/img/logo.svg" alt="drasyl" width="200"/>](https://drasyl.org)

[Website](https://drasyl.org) |
[Documentation](https://docs.drasyl.org) |
[Javadoc](https://api.drasyl.org) |
[Contributing](CONTRIBUTING.md) |
[Changelog](CHANGELOG.md)

[![Build Status](https://git.informatik.uni-hamburg.de/sane-public/drasyl/badges/master/pipeline.svg)](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines)
[![MIT License](https://img.shields.io/badge/license-MIT-blue)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/org.drasyl/drasyl-core.svg)](https://mvnrepository.com/artifact/org.drasyl/drasyl-core)
[![Docker Pulls](https://img.shields.io/docker/pulls/drasyl/drasyl)](https://hub.docker.com/r/drasyl/drasyl)
[![Chocolatey](https://img.shields.io/chocolatey/v/drasyl)](https://chocolatey.org/packages/drasyl)
[![Discord](https://img.shields.io/discord/959492172560891905)](https://discord.gg/2tcZPy7BCu)

# drasyl

drasyl [dʁazy:l] is a general-purpose overlay network framework for rapid development of distributed
P2P applications.

By using drasyl developers can fully concentrate on creating distributed applications. With drasyl,
boundaries between IP-based networks will be eliminated and secure communication channels between
any peers will be provided. Zero-configuration is required to use drasyl. Developers can run a new
drasyl node without having to write configuration files or provide IP addresses of peers.

<img src="https://docs.drasyl.org/master/assets/img/drasyl-architecture.svg" alt="drasyl architecture" width="650px">

# Features

* Provides Communication Channels between any two Nodes (on the Internet).
* Automatic Discovery of Peers running within same Process, Computer, LAN, or the Internet.
* Automatic Handover to most local Communication Channel.
* Overcomes Network Barriers (Stateful Firewalls, NATs).
* UDP Hole Punching.
* Port Mapping (UPnP-IGD, NAT-PMP, PCP).
* Communication is (PFS) encrypted.
* Reacts to Network Connection Changes.
* Asynchronous and Event-Driven.
* Lightweight.
* Extensible.

# Usage & Documentation

* [Getting Started](https://docs.drasyl.org/getting-started/)
* [Configuration](https://docs.drasyl.org/configuration/)
* [Command Line Interface](https://docs.drasyl.org/cli/)

# License

This is free software under the terms of the [MIT License](LICENSE).
