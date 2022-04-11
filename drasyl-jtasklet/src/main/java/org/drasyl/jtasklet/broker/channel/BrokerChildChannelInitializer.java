package org.drasyl.jtasklet.broker.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakePendWritesHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.broker.handler.BrokerResourceRequestHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmHeartbeatHandler;
import org.drasyl.jtasklet.broker.handler.CloseOnConnectionHandshakeError;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public class BrokerChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public BrokerChildChannelInitializer(final PrintStream out,
                                         final PrintStream err,
                                         final Worm<Integer> exitCode,
                                         final Map<IdentityPublicKey, TaskletVm> vms) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.vms = requireNonNull(vms);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();

        // handshake
        p.addLast(new ConnectionHandshakeCodec());
        p.addLast(new ConnectionHandshakeHandler(10_000, false));
        p.addLast(new ConnectionHandshakePendWritesHandler());
        p.addLast(new CloseOnConnectionHandshakeError());

        // arq
        p.addLast(new StopAndWaitArqCodec());
        p.addLast(new StopAndWaitArqHandler(100));
        p.addLast(new ByteToStopAndWaitArqDataCodec());
        p.addLast(new WriteTimeoutHandler(10));

        // codec
        p.addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

        // broker
        p.addLast(new BrokerVmHeartbeatHandler(vms));
        p.addLast(new BrokerResourceRequestHandler(vms));

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable cause) {
                cause.printStackTrace(System.err);
                ctx.fireExceptionCaught(cause);
            }
        });
    }
}
