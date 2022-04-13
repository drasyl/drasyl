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
import org.drasyl.jtasklet.message.ReleaseToken;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.util.Map;

import static java.util.Objects.requireNonNull;

public class BrokerReleaseTokenHandler extends SimpleChannelInboundHandler<ReleaseToken> {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerReleaseTokenHandler.class);
    private final PrintStream out;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerReleaseTokenHandler(final PrintStream out,
                                     final Map<IdentityPublicKey, TaskletVm> vms) {
        this.out = requireNonNull(out);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final ReleaseToken msg) {
        final IdentityPublicKey sender = (IdentityPublicKey) ctx.channel().remoteAddress();
        out.println(sender + " releases token of VM " + msg.getVm() + ".");

        TaskletVm vm = vms.get(msg.getVm());
        if (vm != null) {
            vm.releaseToken();
        }
    }
}
