package my.java.net;

import org.drasyl.AbstractBenchmark;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;

import java.net.InetSocketAddress;

public class InetSocketAddressBenchmark extends AbstractBenchmark {
    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void createUnresolved(final Blackhole blackhole) {
        final InetSocketAddress address = InetSocketAddress.createUnresolved("example.com", 80);
        blackhole.consume(address);
    }

    @Benchmark
    @Threads(1)
    @BenchmarkMode(Mode.Throughput)
    public void createResolve(final Blackhole blackhole) {
        final InetSocketAddress address = new InetSocketAddress("example.com", 80);
        blackhole.consume(address);
    }
}
