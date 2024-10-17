/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.sdon.config.Policy;
import org.drasyl.cli.sdon.message.NodeHello;
import org.drasyl.handler.peers.PeersHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.RandomUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.cli.sdon.handler.SdonNodeHandler.State.JOINED;

public class SdonPoliciesHandler extends ChannelInboundHandlerAdapter {
    public static final Logger LOG = LoggerFactory.getLogger(SdonPoliciesHandler.class);
    public static final int NODE_HELLO_INTERVAL = 5000;
    public static final Map<String, Object> STORE = new HashMap<>();
    private final IdentityPublicKey controller;
    private final PeersHandler peersHandler;
    private final SdonNodeHandler nodeHandler;
    private ChannelHandlerContext ctx;
    private final Set<Policy> policies = new HashSet<>();

    public SdonPoliciesHandler(final IdentityPublicKey controller, final PeersHandler peersHandler, final SdonNodeHandler nodeHandler) {
        this.controller = requireNonNull(controller);
        this.peersHandler = requireNonNull(peersHandler);
        this.nodeHandler = requireNonNull(nodeHandler);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        this.ctx = ctx;

//        LOG.error("handlerAdded");
        ctx.executor().scheduleAtFixedRate(() -> {
//            LOG.error("scheduleAtFixedRate {}", nodeHandler.state);
            if (nodeHandler.state == JOINED) {
                try {
                    final DrasylChannel channel = ((DrasylServerChannel) ctx.channel()).getChannels().get(controller);
                    if (channel != null) {
                        final NodeHello nodeHello = new NodeHello(policies, STORE);
                        LOG.error("Send feedback to controller: `{}`", nodeHello);
                        channel.writeAndFlush(nodeHello).addListener(FIRE_EXCEPTION_ON_FAILURE);
                    }
                    else {
                        LOG.error("Cannot send feedback to controller. No channel present!?");
                    }
                }
                catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        }, RandomUtil.randomInt(0, NODE_HELLO_INTERVAL), NODE_HELLO_INTERVAL, MILLISECONDS);
    }

    public void newPolicies(final Set<Policy> newPolicies) {
        LOG.trace("Got new policies: {}", newPolicies);

        // policies that have to be removed
        final Set<Policy> policiesToBeRemoved = new HashSet<>(policies);
        policiesToBeRemoved.removeAll(newPolicies);
        for (final Policy policy : policiesToBeRemoved) {
            LOG.debug("Remove policy: {}", policy);
            policy.removePolicy(ctx.pipeline());
            policies.remove(policy);
        }

        // policies that have to be added
        final Set<Policy> policiesToBeAdded = new HashSet<>(newPolicies);
        policiesToBeAdded.removeAll(policies);
        for (final Policy policy : policiesToBeAdded) {
            LOG.debug("Add policy: {}", policy);
            policy.addPolicy(ctx.pipeline());
            policies.add(policy);
        }
    }
}
