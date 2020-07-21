package org.drasyl.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNodeComponent;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.DrasylPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.SimpleDuplexHandler;
import org.drasyl.util.NetworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class Monitoring implements DrasylNodeComponent {
    private static final Logger LOG = LoggerFactory.getLogger(Monitoring.class);
    static final String MONITORING_HANDLER = "MONITORING_HANDLER";
    private final PeersManager peersManager;
    private final CompressedPublicKey publicKey;
    private final DrasylPipeline pipeline;
    private final AtomicBoolean opened;
    private final Supplier<MeterRegistry> registrySupplier;
    private MeterRegistry registry;

    public Monitoring(DrasylConfig config,
                      PeersManager peersManager,
                      CompressedPublicKey publicKey,
                      DrasylPipeline pipeline) {
        this(
                peersManager,
                publicKey,
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
               CompressedPublicKey publicKey,
               DrasylPipeline pipeline,
               Supplier<MeterRegistry> registrySupplier,
               AtomicBoolean opened,
               MeterRegistry registry) {
        this.peersManager = peersManager;
        this.publicKey = publicKey;
        this.pipeline = pipeline;
        this.opened = opened;
        this.registrySupplier = registrySupplier;
        this.registry = registry;
    }

    @Override
    public void open() {
        if (opened.compareAndSet(false, true)) {
            LOG.debug("Start Monitoring...");
            registry = registrySupplier.get();

            // add common tags
            registry.config().commonTags(
                    "public_key", publicKey.toString(),
                    "host", ofNullable(NetworkUtil.getLocalHostName()).orElse("")
            ).commonTags();

            // monitor PeersManager
            Gauge.builder("peersManager.peers", peersManager, pm -> pm.getPeers().size()).register(registry);
            Gauge.builder("peersManager.superPeer", peersManager, pm -> pm.getSuperPeerKey() != null ? 1 : 0).register(registry);
            Gauge.builder("peersManager.children", peersManager, pm -> pm.getChildrenKeys().size()).register(registry);
            Gauge.builder("peersManager.grandchildrenRoutes", peersManager, pm -> pm.getGrandchildrenRoutes().size()).register(registry);

            // monitor Pipeline
            pipeline.addFirst(MONITORING_HANDLER, new SimpleDuplexHandler<Object, Event, Object>() {
                private final Map<String, Counter> counters = new HashMap<>();

                @Override
                protected void matchedEventTriggered(HandlerContext ctx, Event event) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.events", event));
                    ctx.fireEventTriggered(event);
                }

                @Override
                protected void matchedRead(HandlerContext ctx,
                                           CompressedPublicKey sender,
                                           Object msg) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.inbound_messages", msg));
                    ctx.fireRead(sender, msg);
                }

                @Override
                protected void matchedWrite(HandlerContext ctx,
                                            CompressedPublicKey recipient,
                                            Object msg,
                                            CompletableFuture<Void> future) {
                    ctx.scheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.outbound_messages", msg));
                    ctx.write(recipient, msg, future);
                }

                private void incrementObjectTypeCounter(String metric, Object o) {
                    Counter counter = counters.computeIfAbsent(o.getClass().getSimpleName(), clazz -> Counter.builder(metric).tag("clazz", clazz).register(registry));
                    counter.increment();
                }
            });
            LOG.debug("Monitoring started.");
        }
    }

    @Override
    public void close() {
        if (opened.compareAndSet(true, false)) {
            LOG.info("Stop Monitoring...");
            pipeline.remove(MONITORING_HANDLER);
            registry.close();
            registry = null;
            LOG.info("Monitoring stopped.");
        }
    }
}
