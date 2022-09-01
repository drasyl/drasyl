# Use libdrasyl with C

We use the GraalVM to build a [native shared library](https://www.graalvm.org/dev/reference-manual/native-image/guides/build-native-shared-library/) for our `DrasylNode` interface.

```bash
# Preconditions
$GRAALVM_HOME/bin/gu install native-image
$GRAALVM_HOME/bin/gu install llvm
$GRAALVM_HOME/bin/gu install llvm-toolchain
```

```bash
# build libdrasyl
./mvnw --batch-mode --errors --fail-at-end --show-version --update-snapshots -DinstallAtEnd=true -DdeployAtEnd=true -Dmaven.javadoc.skip=true -Pnative -DskipTests --projects drasyl-shared-library --also-make package

# build example
$GRAALVM_HOME/languages/llvm/native/bin/clang -I ./ -L ./ -l drasyl -Wl,-rpath ./ -o example example.c

# run example
./example
```
