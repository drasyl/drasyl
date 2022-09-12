/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.node.handler;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.cli.rc.handler.JsonRpc2RequestHandler;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeEvent;
import org.drasyl.node.event.PeerEvent;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletionStage;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.node.message.JsonRpc2Error.INVALID_PARAMS;

/**
 * Allow remote controlling of a {@link DrasylNode} via JSON-RPC 2.0.
 */
public class JsonRpc2DrasylNodeHandler extends JsonRpc2RequestHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRpc2DrasylNodeHandler.class);
    private final DrasylNode node;
    private final Queue<Event> events;

    public JsonRpc2DrasylNodeHandler(final DrasylNode node,
                                     final Queue<Event> events) {
        this.node = requireNonNull(node);
        this.events = requireNonNull(events);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final JsonRpc2Request request) throws Exception {
        LOG.trace("Got request `{}`.", request);

        switch (request.getMethod()) {
            case "start":
                start(ctx, request);
                break;
            case "shutdown":
                shutdown(ctx, request);
                break;
            case "identity":
                identity(ctx, request);
                break;
            case "send":
                send(ctx, request);
                break;
            case "events":
                events(ctx, request);
                break;
            default:
                requestMethodNotFound(ctx, request, request.getMethod());
        }
    }

    private void start(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Start node.");
        final CompletionStage<Void> future = node.start();

        final Object requestId = request.getId();
        if (requestId != null) {
            future.toCompletableFuture().whenComplete((unused, e) -> {
                LOG.trace("Node started.");
                final JsonRpc2Response response;
                if (e == null) {
                    response = new JsonRpc2Response("", requestId);
                }
                else {
                    final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                    response = new JsonRpc2Response(error, requestId);
                }

                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    private void shutdown(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Shutdown node.");
        final CompletionStage<Void> future = node.shutdown();

        final Object requestId = request.getId();
        if (requestId != null) {
            future.toCompletableFuture().whenComplete((unused, e) -> {
                LOG.trace("Node shut down.");
                final JsonRpc2Response response;
                if (e == null) {
                    response = new JsonRpc2Response("", requestId);
                }
                else {
                    final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                    response = new JsonRpc2Response(error, requestId);
                }

                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    private void identity(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Got identity request.");

        final Object requestId = request.getId();
        if (requestId != null) {
            final Identity identity = node.identity();
            final Map<String, Object> result = identityMap(identity);
            final JsonRpc2Response response = new JsonRpc2Response(result, requestId);
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            LOG.trace("Drop identity request as it was sent as notification.");
        }
    }

    private void send(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        final String recipient = request.getParam("recipient");
        final Object payload = request.getParam("payload");
        LOG.trace("Send message to `{}`: `{}`", recipient, payload);

        final Object requestId = request.getId();
        if (recipient == null) {
            if (requestId != null) {
                final JsonRpc2Error error = new JsonRpc2Error(INVALID_PARAMS, "recipient param missing.");
                final JsonRpc2Response response = new JsonRpc2Response(error, requestId);
                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            }
            else {
                LOG.trace("Request is invalid because recipient param is missing. Do not return an error because it was only a notification.");
            }
            return;
        }

        final CompletionStage<Void> future = node.send(recipient, payload);

        if (requestId != null) {
            future.toCompletableFuture().whenComplete((unused, e) -> {
                LOG.trace("Message to `{}` sent", recipient);
                final JsonRpc2Response response;
                if (e == null) {
                    response = new JsonRpc2Response("", requestId);
                }
                else {
                    final JsonRpc2Error error = new JsonRpc2Error(1, e.getMessage());
                    response = new JsonRpc2Response(error, requestId);
                }

                LOG.trace("Send response `{}`.", response);
                ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    private void events(final ChannelHandlerContext ctx, final JsonRpc2Request request) {
        LOG.trace("Got events request.");

        final Object requestId = request.getId();
        if (requestId != null) {
            final List<Map<String, Object>> result = new ArrayList<>();
            while (true) {
                final Event event = events.poll();
                if (event == null) {
                    break;
                }

                final String eventType = event.getClass().getSuperclass().getSimpleName();
                if (event instanceof InboundExceptionEvent) {
                    final InboundExceptionEvent exceptionEvent = (InboundExceptionEvent) event;
                    result.add(Map.of(
                            "type", eventType,
                            "error", Map.of(
                                    "type", exceptionEvent.getError().getClass().getSimpleName(),
                                    "message", exceptionEvent.getError().getMessage()
                            )
                    ));
                }
                else if (event instanceof MessageEvent) {
                    final MessageEvent messageEvent = (MessageEvent) event;
                    result.add(Map.of(
                            "type", eventType,
                            "sender", messageEvent.getSender().toString(),
                            "payload", messageEvent.getPayload()
                    ));
                }
                else if (event instanceof NodeEvent) {
                    final NodeEvent nodeEvent = (NodeEvent) event;
                    result.add(Map.of(
                            "type", eventType,
                            "node", Map.of(
                                    "identity", identityMap(nodeEvent.getNode().getIdentity()),
                                    "port", nodeEvent.getNode().getPort(),
                                    "tcpFallbackPort", nodeEvent.getNode().getTcpFallbackPort()
                            )
                    ));
                }
                else if (event instanceof PeerEvent) {
                    final PeerEvent peerEvent = (PeerEvent) event;
                    result.add(Map.of(
                            "type", eventType,
                            "peer", Map.of(
                                    "address", peerEvent.getPeer().getAddress().toString()
                            )
                    ));
                }
            }

            final JsonRpc2Response response = new JsonRpc2Response(result, requestId);
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            LOG.trace("Drop event request as it was sent as notification.");
        }
    }
}
