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
import org.drasyl.handler.PeersRttHandler;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.message.VmHeartbeat;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class BrokerVmHeartbeatHandler extends SimpleChannelInboundHandler<VmHeartbeat> {
    private static final Logger LOG = LoggerFactory.getLogger(BrokerVmHeartbeatHandler.class);
    private final PrintStream out;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerVmHeartbeatHandler(final PrintStream out,
                                    final Map<IdentityPublicKey, TaskletVm> vms) {
        this.out = requireNonNull(out);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final VmHeartbeat msg) {
        final IdentityPublicKey sender = (IdentityPublicKey) ctx.channel().remoteAddress();
        LOG.debug("Got heartbeat `{}` from `{}`", msg, sender);

        synchronized (vms) {
            TaskletVm vm = vms.get(sender);
            if (vm == null) {
                vm = new TaskletVm(msg.getBenchmark());
                LOG.info("New VM `{}` discovered!", sender);
                vms.put(sender, vm);
            }

            vm.heartbeatReceived(msg.getRttReport(), msg.getToken());
        }
    }
}
