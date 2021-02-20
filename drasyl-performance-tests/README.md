# drasyl performance tests

## Run benchmarks

Each benchmark must have a class name ending with `Benchmark` and inherit
from [`AbstractBenchmark`](src/test/java/org/drasyl/AbstractBenchmark.java).

```shell
mvn -DskipTests=false -Dforks=1 -Dwarmups=1 -Dmeasurements=1 test
```

## Load simulations

Create some nodes that will generate load on our super peers:

```
mvn -DskipTests false package
java -cp drasyl-performance-tests/target/drasyl-performance-tests-0.4.0-jar-with-dependencies.jar \
  -Didentities=../drasyl-non-public/Identities \
  -Dnodes=500 \
  -Dchurn=1500 \
  org.drasyl.test.StartNodes
```
