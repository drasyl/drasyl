# Public Super Peers

Exposed drasyl nodes, configured as super peers and acting as rendezvous servers and relays, are required for discovery of other nodes.

We run some public super peers, so you don't have to.


| **Endpoint**                                                                                         | **Network ID**   | **Used drasyl version**                                                                                                              |
|------------------------------------------------------------------------------------------------------|------------------|--------------------------------------------------------------------------------------------------------------------------------------|
| `udp://production.env.drasyl.org#025fff6f625f5dee816d9f8fe43895479aecfda187cb6a3330894a07e698bc5bd8` | 1                | Latest stable [release](https://github.com/drasyl-overlay/drasyl/releases)                                                           |
| `udp://staging.env.drasyl.org#03096ae3080a369829a44847d5af1f652bef3f9921e9e1bbad64970babe6d3c502`    | -25421           | Latest [nightly](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines?page=1&scope=all&ref=master&status=success)    |

By default, all drasyl nodes are configured to use the super peer `production.env.drasyl.org` and network ID `1`.

