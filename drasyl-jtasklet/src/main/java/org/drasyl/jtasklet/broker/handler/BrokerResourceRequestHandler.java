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
import org.drasyl.jtasklet.broker.TaskletVm;
import org.drasyl.jtasklet.message.ResourceRequest;
import org.drasyl.jtasklet.message.ResourceResponse;
import org.drasyl.util.Pair;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Collectors;

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
        final Pair<IdentityPublicKey, TaskletVm> pair = randomScheduler();
        final IdentityPublicKey publicKey = pair.first();
        final String token;
        if (pair.second() != null) {
            token = pair.second().getToken();
            pair.second().markBusy();
        }
        else {
            token = null;
        }

        final ResourceResponse response = new ResourceResponse(publicKey, token);
        LOG.info("Send resource response `{}` to `{}`", response, ctx.channel().remoteAddress());
        ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
    }

    protected Pair<IdentityPublicKey, TaskletVm> randomScheduler() {
        final Map<IdentityPublicKey, TaskletVm> availableVms = vms.entrySet().stream().filter(e -> !e.getValue().isStale() && !e.getValue().isBusy()).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        if (!availableVms.isEmpty()) {
            final IdentityPublicKey[] publicKeys = availableVms.keySet().toArray(new IdentityPublicKey[0]);
            final int rnd = RANDOM.nextInt(availableVms.size());
            final IdentityPublicKey publicKey = publicKeys[rnd];
            return Pair.of(publicKey, availableVms.get(publicKey));
        }
        else {
            return Pair.of(null, null);
        }
    }
}
