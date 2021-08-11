/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.monitoring;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.annotation.NonNull;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.drasyl.util.network.NetworkUtil;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.drasyl.channel.DefaultDrasylServerChannel.CONFIG_ATTR_KEY;
import static org.drasyl.channel.DefaultDrasylServerChannel.IDENTITY_ATTR_KEY;

/**
 * Monitors various states or events in the drasyl node.
 */
@SuppressWarnings({ "java:S110" })
public class Monitoring extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(Monitoring.class);
    private final Map<String, Counter> counters;
    private final Function<ChannelHandlerContext, MeterRegistry> registrySupplier;
    private MeterRegistry registry;

    Monitoring(final Map<String, Counter> counters,
               final Function<ChannelHandlerContext, MeterRegistry> registrySupplier,
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
                    if (!ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringHostTag().isEmpty()) {
                        hostTag = ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringHostTag();
                    }
                    else {
                        hostTag = ofNullable(NetworkUtil.getLocalHostName()).orElse("");
                    }

                    newRegistry.config().commonTags(
                            "public_key", ctx.channel().attr(IDENTITY_ATTR_KEY).get().getIdentityPublicKey().toString(),
                            "host", hostTag
                    );

                    return newRegistry;
                },
                null
        );
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise);

        if (msg instanceof AddressedMessage) {
            ctx.executor().execute(() -> incrementObjectTypeCounter("pipeline.inbound_messages", ((AddressedMessage<?, ?>) msg).message()));
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        super.channelRead(ctx, msg);

        if (msg instanceof AddressedMessage) {
            ctx.executor().execute(() -> incrementObjectTypeCounter("pipeline.outbound_messages", ((AddressedMessage<?, ?>) msg).message()));
        }
    }

    synchronized void startMonitoring(final ChannelHandlerContext ctx) {
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

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        startMonitoring(ctx);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        stopMonitoring();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx,
                                   final Object evt) {
        ctx.executor().execute(() -> incrementObjectTypeCounter("pipeline.events", evt));
        ctx.fireUserEventTriggered(evt);
    }

    @SuppressWarnings("java:S2972")
    private static class MyInfluxConfig implements InfluxConfig {
        private final ChannelHandlerContext ctx;

        public MyInfluxConfig(final ChannelHandlerContext ctx) {
            this.ctx = requireNonNull(ctx);
        }

        @Override
        @NonNull
        public String uri() {
            return ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringInfluxUri().toString();
        }

        @Override
        public String userName() {
            return ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringInfluxUser();
        }

        @Override
        public String password() {
            return ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringInfluxPassword().toUnmaskedString();
        }

        @Override
        @NonNull
        public String db() {
            return ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringInfluxDatabase();
        }

        @Override
        public boolean autoCreateDb() {
            return false;
        }

        @Override
        @NonNull
        public Duration step() {
            return ctx.channel().attr(CONFIG_ATTR_KEY).get().getMonitoringInfluxReportingFrequency();
        }

        @Override
        public String get(final @NonNull String key) {
            return null;
        }
    }
}
