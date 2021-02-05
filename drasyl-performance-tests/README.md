# drasyl performance tests

Each benchmark must have a class name ending with `Benchmark` and inherit from [`AbstractBenchmark`](src/test/java/org/drasyl/AbstractBenchmark.java).

```shell
mvn -DskipTests=false -Dforks=1 -Dwarmups=1 -Dmeasurements=1 test
```
