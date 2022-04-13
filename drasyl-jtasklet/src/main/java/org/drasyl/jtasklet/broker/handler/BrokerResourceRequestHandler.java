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
package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.message.ResourceRequest;
import org.drasyl.jtasklet.message.ResourceResponse;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;

public class BrokerResourceRequestHandler extends SimpleChannelInboundHandler<ResourceRequest> {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerResourceRequestHandler.class);
    public static final Random RANDOM = new Random();
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerResourceRequestHandler(final Map<IdentityPublicKey, TaskletVm> vms) {
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ResourceRequest msg) {
        LOG.info("Got resource request `{}` from `{}`", msg, ctx.channel().remoteAddress());

        // pick vm
        IdentityPublicKey publicKey = null;
        synchronized (vms) {
            final Set<IdentityPublicKey> availableVms = new HashSet<>(vms.keySet());
            availableVms.removeIf(vm -> vms.get(vm).isStale() || vms.get(vm).isBusy());
            if (!availableVms.isEmpty()) {
                final IdentityPublicKey[] publicKeys = availableVms.toArray(new IdentityPublicKey[availableVms.size()]);
                final int rnd = RANDOM.nextInt(availableVms.size());
                publicKey = publicKeys[rnd];
            }

            if (publicKey != null) {
                vms.get(publicKey).markBusy();
            }
        }

        final ResourceResponse response = new ResourceResponse(publicKey);
        LOG.info("Send resource response `{}` to `{}`", response, ctx.channel().remoteAddress());
        ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }
}
