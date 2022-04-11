package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
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
    private Consumer<Object[]> outputConsumer;

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
        final ChannelPipeline p = ch.pipeline();
        final boolean isBroker = ch.remoteAddress().equals(broker);
        final boolean isProvider = ch.remoteAddress().equals(provider.get());

        if (isBroker || isProvider) {
            // handshake
            p.addLast(new ConnectionHandshakeCodec());
            p.addLast(new ConnectionHandshakeHandler(10_000, true));
            p.addLast(new ConnectionHandshakePendWritesHandler());
            p.addLast(new CloseOnConnectionHandshakeError());

            // arq
            p.addLast(new StopAndWaitArqCodec());
            p.addLast(new StopAndWaitArqHandler(100));
            p.addLast(new ByteToStopAndWaitArqDataCodec());
            p.addLast(new WriteTimeoutHandler(10));

            // chunking
            ch.pipeline().addLast(
                    new MessageChunkEncoder(2),
                    new ChunkedWriteHandler(),
                    new LargeByteBufToChunkedMessageEncoder(1300, 1024 * 1024 * 10),
                    new MessageChunkDecoder(2),
                    new MessageChunksBuffer(1024 * 1024 * 10, 30_000, 10_000),
                    new ChunkedMessageAggregator(1024 * 1024 * 10),
                    new ReassembledMessageDecoder()
            );

            // codec
            p.addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

            // consumer
            if (isBroker) {
                p.addLast(new ResourceRequestHandler(out, provider));
            }
            else if (isProvider) {
                p.addLast(new OffloadTaskHandler(out, source, input, outputConsumer));
            }

            p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));

            // close parent as well
            ch.closeFuture().addListener(f -> ch.parent().close());
        }
    }
}
