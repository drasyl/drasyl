# Configuration

drasyl is designed to work with zero-configuration. However, some use cases require configuration customization.

There are many ways to customize the drasyl (e.g. via environment variables, config file, start parameters, [`DrasylConfig.Builder`](https://github.com/drasyl-overlay/drasyl/tree/master/drasyl-core/src/main/java/org/drasyl/DrasylConfig.java) class) 

The following example shows the usage of the `DrasyConfig.Builder`:
```java
DrasylConfig config = DrasylConfig.newBuilder()
        .loglevel(Level.DEBUG)
        .serverEnabled(false)
        .build();
```

The created `DrasylConfig` object can then be passed to the `DrasylNode` constructor.

An overview of all available configuration parameters are discussed in the following.

Further information regarding formatting can be taken directly from the configuration library that is used internally by drasyl: [https://github.com/lightbend/config](https://github.com/lightbend/config)
