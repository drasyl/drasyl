# Marshalling

drasyl can automatically (un-) marshall given objects. To prevent security risks through unrestricted marshalling, types and packages can be defined in the config that are handled automatically.

## [`allowed-types`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html#marshallingAllowedTypes(java.util.List))

This property is a list of all types that are supported by the codec for the drasyl pipeline.

With this list, a developer guarantees that all classes are secure and cannot be misused as a deserialization gadget in the context of the marshaller.
A reckless implementation of this interface can leave the entire application and all executing machines vulnerable to remote code execution.

!!! info 

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

## [`allow-all-primitives`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html#marshallingAllowAllPrimitives(boolean))

Whether Java's primitive data types should automatically be allowed. If this setting is activated, drasyl automatically (de-)serializes the types: 

* `java.lang.Boolean`
* `java.lang.Character`
* `java.lang.Byte`
* `java.lang.Short`
* `java.lang.Integer`
* `java.lang.Long`
* `java.lang.Float`
* `java.lang.Double`
* `java.lang.Void`
* `java.lang.Boolean#TYPE`
* `java.lang.Character#TYPE`
* `java.lang.Byte#TYPE`
* `java.lang.Short#TYPE`
* `java.lang.Integer#TYPE`
* `java.lang.Long#TYPE`
* `java.lang.Float#TYPE`
* `java.lang.Double#TYPE`
* `java.lang.Void#TYPE`

## [`allow-array-of-defined-types`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html#marshallingAllowArrayOfDefinedTypes(boolean))

Whether of all allowed data types their array representations should be allowed automatically.

## [`allowed-packages`](https://www.javadoc.io/doc/org.drasyl/drasyl-core/latest/org/drasyl/DrasylConfig.Builder.html#marshallingAllowedPackages(java.util.List))

A list of all packages whose classes automatically supported by the default codec of drasyl.

!!! danger "Attention"

    This setting should only be used in absolute exceptions. All classes in the given package are accepted by the standard codec and will try to (de-)serialize. If there is even one insecure implementation in the package, the whole application is vulnerable.
