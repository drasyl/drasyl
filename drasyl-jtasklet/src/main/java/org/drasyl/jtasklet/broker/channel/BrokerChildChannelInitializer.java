package org.drasyl.jtasklet.broker.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndCloseOnExceptionHandler;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakePendWritesHandler;
import org.drasyl.handler.stream.ChunkedMessageAggregator;
import org.drasyl.handler.stream.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.handler.stream.MessageChunkDecoder;
import org.drasyl.handler.stream.MessageChunkEncoder;
import org.drasyl.handler.stream.MessageChunksBuffer;
import org.drasyl.handler.stream.ReassembledMessageDecoder;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand.TaskletVm;
import org.drasyl.jtasklet.broker.handler.BrokerResourceRequestHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmHeartbeatHandler;
import org.drasyl.jtasklet.broker.handler.BrokerVmUpHandler;
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
        // handshake
        ch.pipeline().addLast(
                new ConnectionHandshakeCodec(),
                new ConnectionHandshakeHandler(10_000, false),
                new ConnectionHandshakePendWritesHandler(),
                new CloseOnConnectionHandshakeError()
        );

        // arq
        ch.pipeline().addLast(
                new StopAndWaitArqCodec(),
                new StopAndWaitArqHandler(100),
                new ByteToStopAndWaitArqDataCodec(),
                new WriteTimeoutHandler(10)
        );

        // chunking
        ch.pipeline().addLast(
                new MessageChunkEncoder(2),
                new ChunkedWriteHandler(),
                new LargeByteBufToChunkedMessageEncoder(1300, 1024 * 1024 * 20),
                new MessageChunkDecoder(2),
                new MessageChunksBuffer(1024 * 1024 * 20, 30_000, 15_000),
                new ChunkedMessageAggregator(1024 * 1024 * 20),
                new ReassembledMessageDecoder()
        );

        // codec
        ch.pipeline().addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

        // broker
        ch.pipeline().addLast(
                new BrokerVmHeartbeatHandler(out, vms),
                new BrokerVmUpHandler(out, vms),
                new BrokerResourceRequestHandler(vms),
                new PrintAndCloseOnExceptionHandler(err)
        );
    }
}
