package org.drasyl.jtasklet.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.stream.ChunkedWriteHandler;
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
import org.drasyl.jtasklet.event.ConnectionEstablished;
import org.drasyl.jtasklet.handler.ConnectionEventHandler;
import org.drasyl.jtasklet.handler.PassInboundMessagesToParentHandler;
import org.drasyl.jtasklet.message.TaskletMessage;

import java.io.PrintStream;

import static java.util.Objects.requireNonNull;
import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

public class ChildChannelInitializer extends ChannelInitializer<DrasylChannel> {
    protected static final int ARQ_RETRY_TIMEOUT = 100;
    protected static final int ARQ_WRITE_TIMEOUT = 5;
    protected static final int MSG_CHUNK_SIZE = 1300;
    protected static final int MSG_MAX_SIZE = 1024 * 1024 * 20;
    protected static final int MAX_CHUNKS = 15_000;
    protected static final int CHUNK_FIELD_LENGTH = (int) (Math.log(MAX_CHUNKS) / Math.log(2) / 8) + 1;
    protected static final int HANDSHAKE_TIMEOUT = 10_000;
    protected final PrintStream out;
    protected final boolean activeOpen;

    public ChildChannelInitializer(final PrintStream out, final boolean activeOpen) {
        this.out = requireNonNull(out);
        this.activeOpen = activeOpen;
    }

    @Override
    protected void initChannel(DrasylChannel ch) {
        // handshake
        ch.pipeline().addLast(
                new ConnectionHandshakeCodec(),
                new ConnectionHandshakeHandler(HANDSHAKE_TIMEOUT, activeOpen),
//                new ConnectionHandshakeStatusHandler(out, err),
                new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx,
                                                   Object evt) {
                        if (evt instanceof ConnectionHandshakeCompleted) {
                            ChildChannelInitializer.this.arqStage(ch);
                            ChildChannelInitializer.this.chunkingStage(ch);
                            ChildChannelInitializer.this.codecStage(ch);
                            ChildChannelInitializer.this.handshakeCompletedStage(ch);
                            ctx.pipeline().remove(this);
                        }
                        else {
                            ctx.fireUserEventTriggered(evt);
                        }
                    }
                }
        );
        beforeHandshakePhase(ch);
    }

    private void beforeHandshakePhase(final DrasylChannel ch) {
        ch.pipeline().addLast(new ConnectionEventHandler());
    }

    protected void handshakeCompletedStage(DrasylChannel ch) {
        ch.parent().pipeline().fireUserEventTriggered(new ConnectionEstablished(ch));
        ch.pipeline().addLast(new PassInboundMessagesToParentHandler());
    }

    private void arqStage(final DrasylChannel ch) {
        ch.pipeline().addLast(
                new StopAndWaitArqCodec(),
                new StopAndWaitArqHandler(ARQ_RETRY_TIMEOUT),
                new ByteToStopAndWaitArqDataCodec(),
                new WriteTimeoutHandler(ARQ_WRITE_TIMEOUT)
        );
    }

    private void chunkingStage(final DrasylChannel ch) {
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

    private void codecStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new JacksonCodec<>(JACKSON_MAPPER, TaskletMessage.class));
    }
}
