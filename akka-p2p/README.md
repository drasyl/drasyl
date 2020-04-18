# Akka-P2P

Akka-P2P is an extension for [Akka](https://akka.io/) that provides a fully meshed overlay network. This eliminates the need for Akka systems to
establish a direct network connection with each other to enable communication.

## Requirements

* Java 11
* Akka 2.5.23
* Allow outgoing connections to `relay1.incorum.org:22527`

## Installation

Install Akka-P2P locally:
```bash
mvn install
```

Add Akka-P2P as dependency to your `pom.xml`:
```xml
<dependency>
    <groupId>city.sane</groupId>
    <artifactId>akka-p2p</artifactId>
    <version>1.0</version>
</dependency>
```

## Usage

Set `akka.actor.provider = "city.sane.akka.p2p.P2PActorRefProvider"` in your Akka configuration (It is typically located in
the resource `application.conf`).

Now start an AkkaSystem in the usual way. Since an internet-wide overlay network is used by default, you should ensure that your
system has a unique name. The [ActorSystemNameGenerator](src/main/java/city/sane/akka/p2p/ActorSystemNameGenerator.java) class can be used to generate random or unique names.

```java
ActrSystem system = ActorSystem.create("MyActorSystem221820");
```

### Looking up Actors
`actorSelection(path)` will obtain an `ActorSelection` to an Actor in the overlay network, e.g.:

```java
ActorSelection selection = context.actorSelection("bud://MyActorSystem221820/user/serviceA/worker");
```

As you can see from the example above the following pattern is used to find an actor in the overlay network:

```
bud://<actor system name>/<actor path>
```

Once you obtained a selection to the actor you can interact with it in the same way you would with a local actor, e.g.:

```java
selection.tell("Pretty awesome feature", getSelf());
```

To acquire an `ActorRef` for an `ActorSelection` you need to send a message to the selection and use the `sender` reference of the
reply from the actor. There is a built-in `Identify` message that all Actors will understand and automatically reply to with a
`ActorIdentity` message containing the `ActorRef`. This can also be done with the resolveOne method of the ActorSelection, which
returns a `CompletionStage` of the matching `ActorRef`.

### Documentation

More information can be found in the (still very short) [documentation](doc/README.md).