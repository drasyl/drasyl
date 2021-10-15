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
package org.drasyl.handler.monitoring;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.ScheduledFuture;
import org.drasyl.handler.remote.LocalHostPeerInformation;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.net.http.HttpClient.Version.HTTP_1_1;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Send telemetry data (Neighbour list and, if desired, also the peers' ip addresses) to a given
 * http endpoint.
 */
public class TelemetryHandler extends TopologyHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TelemetryHandler.class);
    private static final InetSocketAddress ZERO_IP = new InetSocketAddress("0.0.0.0", 0);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HTTP_1_1)
            .connectTimeout(ofSeconds(10))
            .build();
    private final HttpClient httpClient;
    private final int submitIntervalSeconds;
    private final URI uri;
    private final boolean includeIp;
    private ScheduledFuture<?> submitJob;

    TelemetryHandler(final Map<DrasylAddress, InetSocketAddress> superPeers,
                     final Map<DrasylAddress, InetSocketAddress> childrenPeers,
                     final Map<DrasylAddress, InetSocketAddress> peers,
                     final HttpClient httpClient,
                     final int submitIntervalSeconds,
                     final URI uri,
                     final boolean includeIp) {
        super(superPeers, childrenPeers, peers);
        this.httpClient = requireNonNull(httpClient);
        this.submitIntervalSeconds = requireNonNull(submitIntervalSeconds);
        this.uri = requireNonNull(uri);
        this.includeIp = includeIp;
        LOG.info("Telemetry enabled: submitIntervalSeconds={}s uri={} includeIp={}", submitIntervalSeconds, uri, includeIp);
    }

    /**
     * @param submitIntervalSeconds how often the current neightbour list should be sent to {@code
     *                              uri}.
     * @param uri                   HTTP/JSON endpoint to which the neighbour list should be sent.
     * @param includeIp             specifies whether the ip addresses of the peers should be
     *                              transmitted or not.
     */
    public TelemetryHandler(final int submitIntervalSeconds,
                            final URI uri,
                            final boolean includeIp) {
        this(new HashMap<>(), new HashMap<>(), new HashMap<>(), HTTP_CLIENT, submitIntervalSeconds, uri, includeIp);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        submitJob = ctx.executor().scheduleWithFixedDelay(() -> submitData(ctx), submitIntervalSeconds, submitIntervalSeconds, SECONDS);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        if (submitJob != null) {
            submitJob.cancel(false);
        }
        ctx.fireChannelInactive();
    }

    private void submitData(final ChannelHandlerContext ctx) {
        Topology topology = topology(ctx);
        if (!includeIp) {
            topology = removeIpAddresses(topology);
        }
        final String body = serializeTopology(topology);
        final HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .timeout(ofSeconds(10))
                .build();
        LOG.debug("Send current topology `{}` to `{}`", topology, uri);
        httpClient.sendAsync(request, BodyHandlers.ofString()).exceptionally(e -> {
            LOG.warn("Unable to send topology to `{}`:", uri, e);
            return null;
        });
    }

    private static Topology removeIpAddresses(final Topology topology) {
        return new Topology(
                topology.address(),
                topology.superPeers().keySet().stream().collect(Collectors.toMap(k -> k, k -> ZERO_IP)),
                topology.childrenPeers().keySet().stream().collect(Collectors.toMap(k -> k, k -> ZERO_IP)),
                topology.peers().keySet().stream().collect(Collectors.toMap(k -> k, k -> ZERO_IP))
        );
    }

    private static String serializeTopology(final Topology topology) {
        return "{" +
                "\"address\":\"" + topology.address() + "\"," +
                "\"superPeers\":{" + serializePeersMap(topology.superPeers()) + "}," +
                "\"childrenPeers\":{" + serializePeersMap(topology.childrenPeers()) + "}," +
                "\"peers\":{" + serializePeersMap(topology.peers()) + "}" +
                "}";
    }

    private static String serializePeersMap(final Map<DrasylAddress, InetSocketAddress> peers) {
        return peers.entrySet().stream()
                .map(entry -> "\"" + entry.getKey().toString() + "\":\"" + LocalHostPeerInformation.serializeAddress(entry.getValue()) + "\"")
                .collect(Collectors.joining(","));
    }
}
