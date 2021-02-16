# Public Super Peers

Exposed drasyl nodes, configured as super peers and acting as rendezvous servers and relays, are required for discovery of other nodes.

We run some public super peers, so you don't have to.

| **Endpoint**  | **Used drasyl version**  |  
|---------------|--------------------------|
| `udp://sp-ham1.drasyl.org:22527?publicKey=0211d40b6e57c8da1780f6b326e9c972cb8f62c522984fb43aec42049efb5b564f&networkId=1` | Latest stable [release](https://github.com/drasyl-overlay/drasyl/releases) | 
| `udp://staging.env.drasyl.org?publicKey=03096ae3080a369829a44847d5af1f652bef3f9921e9e1bbad64970babe6d3c502&networkId=-25421`  | Latest [nightly](https://git.informatik.uni-hamburg.de/sane-public/drasyl/-/pipelines?page=1&scope=all&ref=master&status=success)  |  

By default, all drasyl nodes are configured to use the super peer `sp-ham1.drasyl.org`.

