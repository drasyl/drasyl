# drasyl performance tests

## Run benchmarks

Each benchmark must have a class name ending with `Benchmark` and inherit
from [`AbstractBenchmark`](src/test/java/org/drasyl/AbstractBenchmark.java).

```shell
./mvnw install --activate-profiles fast
cd drasyl-performance-tests
# run all benchmarks
../mvnw -DskipTests=false -Dforks=1 -Dwarmups=1 -Dmeasurements=1 test
# run specific benchmarks
../mvnw -DskipTests=false -Dforks=1 -Dwarmups=1 -Dmeasurements=1 -Dtest='org.drasyl.performance.InetSocketAddressBenchmark,org.drasyl.identity.IdentityPublicKeyBenchmark' test
```

## Build benchmarks jar

```shell
./mvnw --projects drasyl-performance-tests --also-make --activate-profiles fast,benchmark-jar package
# run all benchmarks
java -jar ./drasyl-performance-tests/target/drasyl-benchmarks.jar -rf json -f 1 -wi 1 -i 1
# run specific benchmarks
java -jar ./drasyl-performance-tests/target/drasyl-benchmarks.jar 'org.drasyl.identity.IdentityPublicKeyBenchmark' -rf json -f 1 -wi 1 -i 1
```

## Load simulations

Create some nodes that will generate load on our super peers:

```
mvn -DskipTests false package
java -cp drasyl-performance-tests/target/drasyl-performance-tests-0.4.1-jar-with-dependencies.jar \
  -Didentities=../drasyl-non-public/Identities \
  -Dnodes=500 \
  -Dchurn=1500 \
  org.drasyl.org.drasyl.performance.StartNodes
```
