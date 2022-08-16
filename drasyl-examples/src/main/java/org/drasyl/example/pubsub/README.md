# Pub/Sub

This example how the publish–subscribe pattern can be used with drasyl.

This example consists of a [broker](PubSubBroker.java) that manages all subscriptions and forwards
published messages to subscribers, a [subscriber](PubSubSubscriber.java) interested in given topics,
and
a [publisher](PubSubPublisher.java).

## Usage

1. Start broker: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.pubsub.PubSubBroker"`
1. Start
   subscriber: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.pubsub.PubSubSubscriber" -Dexec.args="<broker_address>"`
1. Start
   publisher: `mvn compile exec:java -Dexec.mainClass="org.drasyl.example.pubsub.PubSubPublisher" -Dexec.args="<broker_address>"`
