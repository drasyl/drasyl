package org.drasyl.jtasklet.provider.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakeIssued;
import org.drasyl.handler.connection.ConnectionHandshakePendWritesHandler;
import org.drasyl.handler.stream.ChunkedMessageAggregator;
import org.drasyl.handler.stream.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.handler.stream.MessageChunkDecoder;
import org.drasyl.handler.stream.MessageChunkEncoder;
import org.drasyl.handler.stream.MessageChunksBuffer;
import org.drasyl.handler.stream.ReassembledMessageDecoder;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.handler.CloseOnConnectionHandshakeError;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.jtasklet.provider.handler.ProcessTaskHandler;
import org.drasyl.jtasklet.provider.handler.VmHeartbeatHandler;
import org.drasyl.jtasklet.provider.runtime.RuntimeEnvironment;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;
import static org.drasyl.util.Preconditions.requirePositive;

public class VmChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final RuntimeEnvironment runtimeEnvironment;
    private final IdentityPublicKey broker;
    private final AtomicReference<PeersRttReport> lastRttReport;
    private final long benchmark;
    private final AtomicReference<Channel> brokerChannel;
    private final AtomicReference<String> token;

    public VmChildChannelInitializer(final PrintStream out,
                                     final PrintStream err,
                                     final Worm<Integer> exitCode,
                                     final RuntimeEnvironment runtimeEnvironment,
                                     final IdentityPublicKey broker,
                                     final AtomicReference<PeersRttReport> lastRttReport,
                                     final long benchmark,
                                     final AtomicReference<Channel> brokerChannel,
                                     final AtomicReference<String> token) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.runtimeEnvironment = requireNonNull(runtimeEnvironment);
        this.broker = broker;
        this.lastRttReport = requireNonNull(lastRttReport);
        this.benchmark = requirePositive(benchmark);
        this.brokerChannel = requireNonNull(brokerChannel);
        this.token = requireNonNull(token);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final boolean isBroker = ch.remoteAddress().equals(broker);

        // handshake
        ch.pipeline().addLast(
                new ConnectionHandshakeCodec(),
                new ConnectionHandshakeHandler(10_000, true),
                new ConnectionHandshakePendWritesHandler(),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(final ChannelHandlerContext ctx,
                                                   final Object evt) {
                        if (evt instanceof ConnectionHandshakeIssued) {
                            out.println("Connect to broker...");
                        }
                        else if (evt instanceof ConnectionHandshakeCompleted) {
                            out.println("Connection to broker established!");
                        }
                        else if (evt instanceof ConnectionHandshakeException) {
                            out.println("Connection failed: " + ((ConnectionHandshakeException) evt).getMessage());
                        }

                        ctx.fireUserEventTriggered(evt);
                    }

                    @Override
                    public void exceptionCaught(final ChannelHandlerContext ctx,
                                                final Throwable cause) {
                        if (cause instanceof ConnectionHandshakeException) {
                            out.println("Connection failed: " + cause.getMessage());
                            ctx.close();
                        }
                        else {
                            ctx.fireExceptionCaught(cause);
                        }
                    }
                }
        );

        // arq
        ch.pipeline().addLast(
                new StopAndWaitArqCodec(),
                new StopAndWaitArqHandler(100),
                new ByteToStopAndWaitArqDataCodec(),
                new WriteTimeoutHandler(15)
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

        // vm
        if (isBroker) {
            // peer is broker
            brokerChannel.set(ch);
            ch.pipeline().addLast(new VmHeartbeatHandler(lastRttReport, benchmark, err, token));

            // close parent as well
            ch.closeFuture().addListener(f -> {
                // reconnect!
                ch.parent().pipeline().addFirst(new SpawnChildChannelToPeer((DrasylServerChannel) ch.parent(), broker));
            });
        }
        else {
            // peer is resource consumer
            ch.pipeline().addLast(new ProcessTaskHandler(runtimeEnvironment, out, brokerChannel, token));
        }

        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void exceptionCaught(final ChannelHandlerContext ctx,
                                        final Throwable cause) {
                if (cause instanceof WriteTimeoutException) {
                    out.println("Connection lost: " + cause);
                    ctx.close();
                }
                else {
                    ctx.fireExceptionCaught(cause);
                }
            }
        });
    }
}
