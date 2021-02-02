# Serialization

The messages that drasyl nodes send to each other are JVM objects. Message passing between nodes
living on the same JVM is straightforward: It is done via reference passing. However, messages that
must leave the JVM to reach a node running on a different host must go through some form of
serialization (i.e., the objects must be converted to and from byte arrays).

The serialization mechanism in drasyl allows you to write custom serializers and to define which
serializer should be used for what.

## Configuration

In order for drasyl to know which serializer to use for what, you need to edit your configuration:

In the `drasyl.serialization.serializers`-section, bind names to implementations of
[`Serializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/Serializer.html)
you want to use, like this:

```
drasyl {
  serialization {
    serializers {
      java = "org.drasyl.serialization.JavaSerializer"
      jackson-json = "org.drasyl.serialization.JacksonJsonSerializer"
      proto = "org.drasyl.serialization.ProtobufSerializer"
      myown = "docs.serialization.MyOwnSerializer"
    }
  }
}
```

After you’ve bound names to different implementations of
[`Serializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/Serializer.html)
you need to wire which classes should be serialized using which serializer. This is done in
the `drasyl.serialization.bindings.inbound`-section for inbound messages and
`drasyl.serialization.bindings.outbound`-section for outbound messages:

```
drasyl {
  serialization {
    serializers {
      java = "org.drasyl.serialization.JavaSerializer"
      jackson-json = "org.drasyl.serialization.JacksonJsonSerializer"
      proto = "org.drasyl.serialization.ProtobufSerializer"
      myown = "docs.serialization.MyOwnSerializer"
    }
    
    bindings {
        inbound {
          "[Ljava.lang.String;" = java
          "docs.serialization.JsonSerializable" = jackson-json
          "com.google.protobuf.Message" = proto
          "docs.serialization.MyOwnSerializable" = myown
        }
        
        outbound {
          "[Ljava.lang.String;" = java
          "docs.serialization.JsonSerializable" = jackson-json
          "com.google.protobuf.Message" = proto
          "docs.serialization.MyOwnSerializable" = myown
        }
    }
  }
}
```

You only need to specify the name of an interface or abstract base class of the messages.

## Build-in serializers

drasyl ships some build-in serializers for the most common object types. Serializers are already
defined and bound for the most common message types (Java primives, their wrapper classes, strings,
and byte arrays). On the other hand, some serializers have to be bound manually to the desired
classes.

### Enabled and bound to the serializable classes

| Name   | Class   | Serializable Classes   |
|:-------|:--------|:-----------------------|
| `primitive-boolean` | [`BooleanSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/BooleanSerializer.html)         | `Boolean`, `boolean` |
| `primitive-byte`    | [`ByteSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/ByteSerializer.html)               | `Byte`, `byte`       |
| `primitive-char`    | [`CharacterSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/CharacterSerializer.html)     | `Character`, `char`  |
| `primitive-float`   | [`FloatSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/FloatSerializer.html)             | `Float`, `float`     |
| `primitive-int`     | [`IntegerSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/IntegerSerializer.html)         | `Integer`, `int`     |
| `primitive-long`    | [`LongSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/LongSerializer.html)               | `Long`, `long`       |
| `primitive-short`   | [`ShortSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/ShortSerializer.html)             | `Short`, `short`     |
| `bytes`             | [`ByteArraySerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/ByteArraySerializer.html)     | `byte[]`             |
| `string`            | [`StringSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/StringSerializer.html)           | `String`             |

So if you only send object types that are included in this table, you don't need to configure
anything!

### Enabled and must be manually bound

| Name   | Class   | Serializable Classes   |
|:-------|:--------|:-----------------------|
| `java`              | [`JavaSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/JavaSerializer.html)           | [`Serializable`](https://docs.oracle.com/javase/7/docs/api/java/io/Serializable.html)       |
| `jackson-json`      | [`JacksonJsonSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/JacksonJsonSerializer.html) | all Jackson-compatible classes       |
| `proto`             | [`ProtobufSerializer`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/serialization/ProtobufSerializer.html)       | Protobuf [`Message`](https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/Message)        |

* [Serialization with Jackson](https://github.com/FasterXML/jackson) is a good choice in many cases
  and our recommendation if you don’t have other preferences.
* [Google Protocol Buffers](https://developers.google.com/protocol-buffers) is good if you want more
  control over the schema evolution of your messages, but it requires more work to develop and
  maintain the mapping between serialized representation and domain representation.

Before binding arbitrary classes to a Serializer, please read the security notes below.

## Security notes

With this configuration, a developer guarantees that all classes are secure and cannot be misused as
a deserialization gadget in the context of the Serializer. A reckless implementation of a permitted
class can leave the entire application and all executing machines vulnerable to remote code
execution.

!!! important

    An attacker is in general interested in all "non-pure" methods, which have promising side
    effects. A method is "pure" if:
    
    * The execution of the function has no side effects, and
    * the return value of the function depends only on the input parameters passed to the function.
    
    For example, a vulnerability could be a setter or getter that connects to a database. 
    A vulnerable class is for example the ch.qos.logback.core.db.DriverManagerConnectionSource. 
    An attacker can choose the URL arbitrarily. By calling getConnection, Server Side Request Forgery (SSRF) and DOS attacks can occur.
    
    You can find more about this in the following literature:
    
    * [Java Unmarshaller Security - Turning your data into code execution by Moritz Bechler](https://raw.githubusercontent.com/mbechler/marshalsec/master/marshalsec.pdf)
    * [Automated Discovery of Deserialization Gadget Chains by Ian Haken](https://i.blackhat.com/us-18/Thu-August-9/us-18-Haken-Automated-Discovery-of-Deserialization-Gadget-Chains-wp.pdf)
    * [Marshalling Pickles by Chris Frohoff and Garbriel Lawrence](https://frohoff.github.io/appseccali-marshalling-pickles/)