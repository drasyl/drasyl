package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakePendWritesHandler;
import org.drasyl.handler.stream.ChunkedMessageAggregator;
import org.drasyl.handler.stream.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.handler.stream.MessageChunkDecoder;
import org.drasyl.handler.stream.MessageChunkEncoder;
import org.drasyl.handler.stream.MessageChunksBuffer;
import org.drasyl.handler.stream.ReassembledMessageDecoder;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.handler.CloseOnConnectionHandshakeError;
import org.drasyl.jtasklet.consumer.handler.OffloadTaskHandler;
import org.drasyl.jtasklet.consumer.handler.ResourceRequestHandler;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public class ConsumerChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final AtomicReference<IdentityPublicKey> provider;
    private final Consumer<Object[]> outputConsumer;

    @SuppressWarnings("java:S107")
    public ConsumerChildChannelInitializer(final PrintStream out,
                                           final PrintStream err,
                                           final Worm<Integer> exitCode,
                                           final IdentityPublicKey broker,
                                           final String source,
                                           final Object[] input,
                                           final AtomicReference<IdentityPublicKey> provider,
                                           final Consumer<Object[]> outputConsumer) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.provider = requireNonNull(provider);
        this.outputConsumer = requireNonNull(outputConsumer);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final boolean isBroker = ch.remoteAddress().equals(broker);
        final boolean isProvider = ch.remoteAddress().equals(provider.get());

        if (isBroker || isProvider) {
            // handshake
            ch.pipeline().addLast(
                    new ConnectionHandshakeCodec(),
                    new ConnectionHandshakeHandler(10_000, true),
                    new ConnectionHandshakePendWritesHandler(),
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(final ChannelHandlerContext ctx,
                                                    final Throwable cause) {
                            if (cause instanceof ConnectionHandshakeException) {
                                cause.printStackTrace(err);
                                ctx.close();
                                exitCode.trySet(1);
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

            // consumer
            if (isBroker) {
                ch.pipeline().addLast(new ResourceRequestHandler(out, provider));
            }
            else if (isProvider) {
                ch.pipeline().addLast(new OffloadTaskHandler(out, source, input, outputConsumer));
            }

            ch.pipeline().addLast(new PrintAndExitOnExceptionHandler(err, exitCode));

            // close parent as well
            ch.closeFuture().addListener(f -> ch.parent().close());
        }
    }
}
