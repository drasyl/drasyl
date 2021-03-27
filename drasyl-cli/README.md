# Command Line Tool

See https://docs.drasyl.org/cli/ for more information.

## Native Image

The CLI can also be build to a so-called *native image*:

    Native Image is a technology to ahead-of-time compile Java code to a standalone executable,
    called a native image. This executable includes the application classes, classes from its
    dependencies, runtime library classes, and statically linked native code from JDK. It does not
    run on the Java VM, but includes necessary components like memory management, thread scheduling,
    and so on from a different runtime system, called “Substrate VM”. Substrate VM is the name for
    the runtime components (like the deoptimizer, garbage collector, thread scheduling etc.). The
    resulting program has faster startup time and lower runtime memory overhead compared to a JVM.

Source: https://www.graalvm.org/reference-manual/native-image/

### Build Process

1. [Install GraalVM](https://www.graalvm.org/docs/getting-started/)
1. [Install Native Image and meet the prerequisites](https://www.graalvm.org/reference-manual/native-image/#install-native-image)
1. Make sure that `JAVA_HOME` points to the GraalVM and `native-image` exists in `PATH`.
1. Execute `./mvnw -Pnative package` from the drasyl root directory.
1. If everything went well, you should now find a `drasyl` or `drasyl.exe` executable in the root
   directory.
