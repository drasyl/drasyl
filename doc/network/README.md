# Overlay Network

In order to allow easy integration with arbitrary applications, the Overlay Network has a minimalist design. The main task is to provide transport channels
between any nodes in the world. Exposed super peers are used to discover other nodes. If a direct connection between two nodes is not possible, the traffic is
forwarded via a super peer. Each node generates an identity at the first start, through which the node can be uniquely addressed.

The network gives no guarantee that messages sent will arrive, arrive in the correct order or arrive exactly once.

## BPMN Diagrams

We have created several BPMN diagrams for important components/processes within the overlay network

* [Node Lifecycle](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Register at Super Peer](https://cawemo.com/share/6bd4ccf2-7d15-493e-9b9a-5cd7041d34e1)
* [Send, Relay & Receive Message](https://cawemo.com/share/442a5a0e-a922-4dd3-920a-fa625c8e1fe5)
* [Establish Direct Connection (P2P)](https://cawemo.com/share/7c80ab60-da67-4438-bf75-e2c9c1c7e0fb)