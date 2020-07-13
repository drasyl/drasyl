package org.drasyl.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimplexDuplexHandler;
import org.drasyl.util.NetworkUtil;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

/**
 * Monitors various states or events in the drasyl Node.
 */
public class Monitoring implements AutoCloseable {
    static final String MONITORING_HANDLER = "MONITORING_HANDLER";
    private final PeersManager peersManager;
    private final Supplier<CompressedPublicKey> publicKeySupplier;
    private final DrasylPipeline pipeline;
    private final AtomicBoolean opened;
    private final Supplier<MeterRegistry> registrySupplier;
    private MeterRegistry registry;

    public Monitoring(DrasylConfig config,
                      PeersManager peersManager,
                      Supplier<CompressedPublicKey> publicKeySupplier,
                      DrasylPipeline pipeline) {
        this(
                peersManager,
                publicKeySupplier,
                pipeline,
                () -> new InfluxMeterRegistry(new InfluxConfig() {
                    @Override
                    public String uri() {
                        return config.getMonitoringInfluxUri();
                    }

                    @Override
                    public String userName() {
                        return config.getMonitoringInfluxUser();
                    }

                    @Override
                    public String password() {
                        return config.getMonitoringInfluxPassword();
                    }

                    @Override
                    public String db() {
                        return config.getMonitoringInfluxDatabase();
                    }

                    @Override
                    public boolean autoCreateDb() {
                        return false;
                    }

                    @Override
                    public Duration step() {
                        return config.getMonitoringInfluxReportingFrequency();
                    }

                    @Override
                    public String get(String key) {
                        return null;
                    }
                }, Clock.SYSTEM), new AtomicBoolean(),
                null
        );
    }

    Monitoring(PeersManager peersManager,
               Supplier<CompressedPublicKey> publicKeySupplier,
               DrasylPipeline pipeline,
               Supplier<MeterRegistry> registrySupplier,
               AtomicBoolean opened,
               MeterRegistry registry) {
        this.peersManager = peersManager;
        this.publicKeySupplier = publicKeySupplier;
        this.pipeline = pipeline;
        this.opened = opened;
        this.registrySupplier = registrySupplier;
        this.registry = registry;
    }

    public void open() {
        if (opened.compareAndSet(false, true)) {
            registry = registrySupplier.get();

            // add common tags
            registry.config().commonTags(
                    "public_key", publicKeySupplier.get().toString(),
                    "host", ofNullable(NetworkUtil.getLocalHostName()).orElse("")
            ).commonTags();

            // monitor PeersManager
            Gauge.builder("peersManager.peers", peersManager, pm -> pm.getPeers().size()).register(registry);
            Gauge.builder("peersManager.superPeer", peersManager, pm -> pm.getSuperPeerKey() != null ? 1 : 0).register(registry);
            Gauge.builder("peersManager.children", peersManager, pm -> pm.getChildrenKeys().size()).register(registry);
            Gauge.builder("peersManager.grandchildrenRoutes", peersManager, pm -> pm.getGrandchildrenRoutes().size()).register(registry);

            // monitor Pipeline
            pipeline.addFirst(MONITORING_HANDLER, new SimplexDuplexHandler<ApplicationMessage, Event, ApplicationMessage>() {
                private final Map<String, Counter> counters = new HashMap<>();

                @Override
                protected void matchedEventTriggered(HandlerContext ctx, Event event) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.events", event));
                    ctx.fireEventTriggered(event);
                }

                @Override
                protected void matchedRead(HandlerContext ctx, ApplicationMessage msg) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.inbound_messages", msg));
                    ctx.fireRead(msg);
                }

                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            ApplicationMessage msg,
                                            CompletableFuture<Void> future) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.outbound_messages", msg));
                    ctx.write(msg, future);
                }

                private void incrementObjectTypeCounter(String metric, Object o) {
                    Counter counter = counters.computeIfAbsent(o.getClass().getSimpleName(), clazz -> Counter.builder(metric).tag("clazz", clazz).register(registry));
                    counter.increment();
                }
            });
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            pipeline.remove(MONITORING_HANDLER);
            registry.close();
            registry = null;
        }
    }
}
