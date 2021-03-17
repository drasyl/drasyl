/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.skeleton.SimpleDuplexHandler;
import org.drasyl.util.NetworkUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;

/**
 * Monitors various states or events in the drasyl node.
 */
@SuppressWarnings({ "java:S110" })
public class Monitoring extends SimpleDuplexHandler<Object, Object, Address> {
    public static final String MONITORING_HANDLER = "MONITORING_HANDLER";
    private static final Logger LOG = LoggerFactory.getLogger(Monitoring.class);
    private final Map<String, Counter> counters;
    private final Function<HandlerContext, MeterRegistry> registrySupplier;
    private MeterRegistry registry;

    Monitoring(final Map<String, Counter> counters,
               final Function<HandlerContext, MeterRegistry> registrySupplier,
               final MeterRegistry registry) {
        this.counters = requireNonNull(counters);
        this.registrySupplier = requireNonNull(registrySupplier);
        this.registry = registry;
    }

    public Monitoring() {
        this(
                new HashMap<>(),
                ctx -> {
                    final MeterRegistry newRegistry = new InfluxMeterRegistry(new MyInfluxConfig(ctx), Clock.SYSTEM);

                    // add common tags
                    final String hostTag;
                    if (!ctx.config().getMonitoringHostTag().isEmpty()) {
                        hostTag = ctx.config().getMonitoringHostTag();
                    }
                    else {
                        hostTag = ofNullable(NetworkUtil.getLocalHostName()).orElse("");
                    }

                    newRegistry.config().commonTags(
                            "public_key", ctx.identity().getPublicKey().toString(),
                            "host", hostTag
                    );

                    // monitor PeersManager
                    Gauge.builder("peersManager.peers", ctx.peersManager(), pm -> pm.getPeers().size()).register(newRegistry);
                    Gauge.builder("peersManager.superPeers", ctx.peersManager(), pm -> pm.getSuperPeers().size()).register(newRegistry);
                    Gauge.builder("peersManager.children", ctx.peersManager(), pm -> pm.getChildren().size()).register(newRegistry);

                    return newRegistry;
                },
                null
        );
    }

    @Override
    public void onEvent(final HandlerContext ctx,
                        final Event event,
                        final CompletableFuture<Void> future) {
        ctx.independentScheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.events", event));

        if (event instanceof NodeUpEvent) {
            startMonitoring(ctx);
        }
        else if (event instanceof NodeUnrecoverableErrorEvent || event instanceof NodeDownEvent) {
            stopMonitoring();
        }

        // passthrough event
        ctx.passEvent(event, future);
    }

    @Override
    protected void matchedInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) {
        ctx.independentScheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.inbound_messages", msg));

        // passthrough message
        ctx.passInbound(sender, msg, future);
    }

    @Override
    protected void matchedOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
        ctx.independentScheduler().scheduleDirect(() -> incrementObjectTypeCounter("pipeline.outbound_messages", msg));

        // passthrough message
        ctx.passOutbound(recipient, msg, future);
    }

    synchronized void startMonitoring(final HandlerContext ctx) {
        if (registry == null) {
            LOG.debug("Start Monitoring...");
            registry = registrySupplier.apply(ctx);

            LOG.debug("Monitoring started.");
        }
    }

    synchronized void stopMonitoring() {
        if (registry != null) {
            LOG.debug("Stop Monitoring...");
            registry.close();
            registry = null;
            LOG.debug("Monitoring stopped.");
        }
    }

    private void incrementObjectTypeCounter(final String metric, final Object o) {
        if (registry != null) {
            final Counter counter = counters.computeIfAbsent(o.getClass().getSimpleName(), clazz -> Counter.builder(metric).tag("clazz", clazz).register(registry));
            counter.increment();
        }
    }

    @SuppressWarnings("java:S2972")
    private static class MyInfluxConfig implements InfluxConfig {
        private final HandlerContext ctx;

        public MyInfluxConfig(final HandlerContext ctx) {
            this.ctx = requireNonNull(ctx);
        }

        @Override
        @NonNull
        public String uri() {
            return ctx.config().getMonitoringInfluxUri().toString();
        }

        @Override
        public String userName() {
            return ctx.config().getMonitoringInfluxUser();
        }

        @Override
        public String password() {
            return ctx.config().getMonitoringInfluxPassword();
        }

        @Override
        @NonNull
        public String db() {
            return ctx.config().getMonitoringInfluxDatabase();
        }

        @Override
        public boolean autoCreateDb() {
            return false;
        }

        @Override
        @NonNull
        public Duration step() {
            return ctx.config().getMonitoringInfluxReportingFrequency();
        }

        @Override
        public String get(final @NonNull String key) {
            return null;
        }
    }
}
