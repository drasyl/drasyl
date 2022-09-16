# Shared Library

We use the GraalVM to build a [shared library with native image](https://www.graalvm.org/dev/reference-manual/native-image/guides/build-native-shared-library/) for our [`DrasylNode`](https://api.drasyl.org/master/org/drasyl/node/DrasylNode.html) interface.

## Native Image

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
1. [Install Native Image and meet the prerequisites](https://www.graalvm.org/dev/reference-manual/native-image/guides/build-native-shared-library/)
1. Make sure that `JAVA_HOME` points to the GraalVM and `native-image` exists in `PATH`.
1. Execute `./mvnw -DskipTests -Pnative --projects drasyl-shared-library --also-make package` from the drasyl
   root directory.
1. If everything went well, you should now find a `libdrasyl.dylib`, `libdrasyl.so`, or `libdrasyl.dll` shared library in the root
   directory.

### Example
```bash
# build
$JAVA_HOME/languages/llvm/native/bin/clang -I ./ -L ./ -l drasyl -Wl,-rpath ./ -o example ./drasyl-shared-library/examples/c/example.c

# run
./example
```
