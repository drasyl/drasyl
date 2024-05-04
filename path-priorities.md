| Priority | Location                                                                                                                                                     |
|---------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
|     `20` | [IntraVmDiscovery](drasyl-core/src/main/java/org/drasyl/handler/discovery/IntraVmDiscovery.java)                                                             |
|     `70` | [StaticRoutesHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/StaticRoutesHandler.java)                                                          |
|     `80` | [LocalHostDiscovery](drasyl-core/src/main/java/org/drasyl/handler/remote/LocalHostDiscovery.java)                                                            |
|     `90` | [LocalNetworkDiscovery](drasyl-core/src/main/java/org/drasyl/handler/remote/LocalNetworkDiscovery.java)                                                      |
|     `95` | [TraversingInternetDiscoveryChildrenHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/internet/TraversingInternetDiscoveryChildrenHandler.java)   |
|     `95` | [TraversingInternetDiscoverySuperPeerHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/internet/TraversingInternetDiscoverySuperPeerHandler.java) |
|    `100` | [InternetDiscoveryChildrenHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/internet/InternetDiscoveryChildrenHandler.java)                       |
|    `100` | [InternetDiscoverySuperPeerHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/internet/InternetDiscoverySuperPeerHandler.java)                     |
|    `110` | [TcpClient](drasyl-core/src/main/java/org/drasyl/handler/remote/tcp/TcpClient.java)                                                                          
|    `110` | [TcpServer](drasyl-core/src/main/java/org/drasyl/handler/remote/tcp/TcpServer.java)                                                                          |
|    `200` | [UnconfirmedAddressResolveHandler](drasyl-core/src/main/java/org/drasyl/handler/remote/internet/UnconfirmedAddressResolveHandler.java)                       |
