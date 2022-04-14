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
import org.drasyl.handler.connection.ConnectionHandshakeCompleted;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.stream.ChunkedMessageAggregator;
import org.drasyl.handler.stream.LargeByteBufToChunkedMessageEncoder;
import org.drasyl.handler.stream.MessageChunkDecoder;
import org.drasyl.handler.stream.MessageChunkEncoder;
import org.drasyl.handler.stream.MessageChunksBuffer;
import org.drasyl.handler.stream.ReassembledMessageDecoder;
import org.drasyl.jtasklet.broker.handler.JTaskletConnectionHandshakeHandler;
import org.drasyl.jtasklet.message.TaskletMessage;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public abstract class AbstractChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractChildChannelInitializer.class);
    protected static final int ARQ_RETRY_TIMEOUT = 100;
    protected static final int ARQ_WRITE_TIMEOUT = 5;
    protected static final int MSG_CHUNK_SIZE = 1300;
    protected static final int MSG_MAX_SIZE = 1024 * 1024 * 20;
    protected static final int MAX_CHUNKS = 15_000;
    protected static final int CHUNK_FIELD_LENGTH = (int) (Math.log(MAX_CHUNKS) / Math.log(2) / 8) + 1;
    protected final PrintStream out;
    protected final boolean activeOpen;

    protected AbstractChildChannelInitializer(final PrintStream out, final boolean activeOpen) {
        this.out = requireNonNull(out);
        this.activeOpen = activeOpen;
    }

    @Override
    protected void initChannel(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new ConnectionHandshakeCodec(),
                new ConnectionHandshakeHandler(10_000, activeOpen),
                new JTaskletConnectionHandshakeHandler(out),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx,
                                                   Object evt) {
                        if (evt instanceof ConnectionHandshakeCompleted) {
                            firstStage(ch);
                            arqStage(ch);
                            chunkingStage(ch);
                            codecStage(ch);
                            lastStage(ch);
                            ctx.pipeline().remove(this);
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                }
        );
    }

    protected void firstStage(final DrasylChannel ch) {
        // NOOP
    }

    protected void arqStage(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new StopAndWaitArqCodec(),
                new StopAndWaitArqHandler(ARQ_RETRY_TIMEOUT),
                new ByteToStopAndWaitArqDataCodec(),
                new WriteTimeoutHandler(ARQ_WRITE_TIMEOUT)
        );
    }

    protected void chunkingStage(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new MessageChunkEncoder(CHUNK_FIELD_LENGTH),
                new ChunkedWriteHandler(),
                new LargeByteBufToChunkedMessageEncoder(MSG_CHUNK_SIZE, MSG_MAX_SIZE),
                new MessageChunkDecoder(CHUNK_FIELD_LENGTH),
                new MessageChunksBuffer(MSG_MAX_SIZE, 30_000, MAX_CHUNKS),
                new ChunkedMessageAggregator(MSG_MAX_SIZE),
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
                    LOG.debug("Connection lost: " + cause);
                    ctx.close();
                }
                else {
                    cause.printStackTrace();
                    ctx.close();
                }
            }
        });
    }
}
