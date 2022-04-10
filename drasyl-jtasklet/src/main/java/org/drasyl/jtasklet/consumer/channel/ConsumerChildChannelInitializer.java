package org.drasyl.jtasklet.consumer.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.handler.PrintAndExitOnExceptionHandler;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeEvent;
import org.drasyl.handler.connection.ConnectionHandshakeException;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakePendWritesHandler;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.consumer.handler.OffloadTaskHandler;
import org.drasyl.jtasklet.consumer.handler.ResourceRequestHandler;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

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

    public ConsumerChildChannelInitializer(final PrintStream out,
                                           final PrintStream err,
                                           final Worm<Integer> exitCode,
                                           final IdentityPublicKey broker,
                                           final String source,
                                           final Object[] input,
                                           final AtomicReference<IdentityPublicKey> provider) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.provider = requireNonNull(provider);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        final ChannelPipeline p = ch.pipeline();
        final boolean isBroker = ch.remoteAddress().equals(broker);
        final boolean isProvider = ch.remoteAddress().equals(provider.get());

        if (isBroker || isProvider) {
            // arq
            p.addLast(new StopAndWaitArqCodec());
            p.addLast(new StopAndWaitArqHandler(100));
            p.addLast(new ByteToStopAndWaitArqDataCodec());
            p.addLast(new WriteTimeoutHandler(10));

            // handshake
            p.addLast(new ConnectionHandshakeCodec());
            p.addLast(new ConnectionHandshakeHandler(10_000, true));
            p.addLast(new ConnectionHandshakePendWritesHandler());
            p.addLast(new ChannelInboundHandlerAdapter() {
                @Override
                public void exceptionCaught(final ChannelHandlerContext ctx,
                                            final Throwable cause) {
                    if (cause instanceof ConnectionHandshakeException) {
                        ctx.close();
                    }
                    else {
                        ctx.fireExceptionCaught(cause);
                    }
                }
            });

            // codec
            p.addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));

            // consumer
            if (isBroker) {
                p.addLast(new ResourceRequestHandler(out, provider));
            }
            else if (isProvider) {
                p.addLast(new OffloadTaskHandler(out, source, input));
            }

            p.addLast(new PrintAndExitOnExceptionHandler(err, exitCode));

            // close parent as well
            ch.closeFuture().addListener(f -> ch.parent().close());
        }
    }
}
