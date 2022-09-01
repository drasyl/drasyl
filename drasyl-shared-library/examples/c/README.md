```bash
gu install native-image
gu install llvm
gu install llvm-toolchain

# build libdrasyl.h
./mvnw --batch-mode --errors --fail-at-end --show-version --update-snapshots -DinstallAtEnd=true -DdeployAtEnd=true -Pnative -DskipTests --projects drasyl-shared-library --also-make package

# build example
$GRAALVM_HOME/languages/llvm/native/bin/clang -I ./ -L ./ -l drasyl -Wl,-rpath ./ -o example ./drasyl-shared-library/examples/c/example.c
```
