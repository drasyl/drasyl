package org.drasyl.jtasklet.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.drasyl.channel.DrasylChannel;
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
import org.drasyl.jtasklet.broker.handler.JTaskletConnectionHandshakeHandler;
import org.drasyl.jtasklet.message.TaskletMessage;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public abstract class AbstractChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    protected final PrintStream out;

    protected AbstractChildChannelInitializer(PrintStream out) {
        this.out = requireNonNull(out);
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        firstStage(ch);
        handshakeStage(ch);
        arqStage(ch);
        chunkingStage(ch);
        codecStage(ch);
        lastStage(ch);
    }

    protected void firstStage(final DrasylChannel ch) {
        // NOOP
    }

    protected void handshakeStage(final DrasylChannel ch, final boolean activeOpen) {
        ch.pipeline().addLast(
                new ConnectionHandshakeCodec(),
                new ConnectionHandshakeHandler(10_000, activeOpen),
                new ConnectionHandshakePendWritesHandler(),
                new JTaskletConnectionHandshakeHandler(out));
    }

    protected void handshakeStage(DrasylChannel ch) {
        handshakeStage(ch, true);
    }

    protected void arqStage(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new StopAndWaitArqCodec(),
                new StopAndWaitArqHandler(100),
                new ByteToStopAndWaitArqDataCodec(),
                new WriteTimeoutHandler(15)
        );
    }

    protected void chunkingStage(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new MessageChunkEncoder(2),
                new ChunkedWriteHandler(),
                new LargeByteBufToChunkedMessageEncoder(1300, 1024 * 1024 * 20),
                new MessageChunkDecoder(2),
                new MessageChunksBuffer(1024 * 1024 * 20, 30_000, 15_000),
                new ChunkedMessageAggregator(1024 * 1024 * 20),
                new ReassembledMessageDecoder()
        );
    }

    protected void codecStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));
    }

    protected void lastStage(final DrasylChannel ch) {
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
