/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdo.channel;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.drasyl.channel.ConnectionChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.cli.sdo.event.ControllerHandshakeFailed;
import org.drasyl.cli.sdo.handler.SdoMessageChildHandler;
import org.drasyl.cli.sdo.handler.SdoPoliciesHandler;
import org.drasyl.cli.sdo.handler.policy.TunPolicyHandler;
import org.drasyl.cli.sdo.handler.policy.TunPolicyHandler.DrasylToTunHandler;
import org.drasyl.cli.sdo.message.SdoMessage;
import org.drasyl.cli.tun.handler.TunPacketCodec;
import org.drasyl.handler.codec.JacksonCodec;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;

public class SdoNodeChildChannelInitializer extends ConnectionChannelInitializer {
    private final PrintStream out;
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey controller;

    public SdoNodeChildChannelInitializer(final PrintStream out,
                                          final PrintStream err,
                                          final Worm<Integer> exitCode,
                                          final IdentityPublicKey controller) {
        this.out = requireNonNull(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.controller = requireNonNull(controller);
    }

    @Override
    protected void handshakeCompleted(final ChannelHandlerContext ctx) {
        final ChannelPipeline p = ctx.pipeline();

//        p.addLast(new JacksonCodec<>(SdoMessage.class));
//        p.addLast(new SdoMessageChildHandler());
    }

    @Override
    protected void handshakeFailed(final ChannelHandlerContext ctx, final Throwable cause) {
        if (controller.equals(ctx.channel().remoteAddress())) {
            // handshake with controller failed
            ctx.channel().parent().pipeline().fireUserEventTriggered(new ControllerHandshakeFailed(cause));
        }
        else {
            // handshake with other node failed
            out.println("Close connection to " + ctx.channel().remoteAddress() + " as handshake was not fulfilled within " + config.userTimeout().toMillis() + "ms.");
            ctx.close();
        }
    }

    @Override
    protected void initChannel(final DrasylChannel ch) throws Exception {
        super.initChannel(ch);

        final ChannelPipeline p = ch.pipeline();

        p.addLast(new TunPacketCodec());
        p.addLast(new DrasylToTunHandler());
        p.addLast(new JacksonCodec<>(SdoMessage.class));
        p.addLast(new SdoMessageChildHandler());

        p.addLast(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                super.channelActive(ctx);

                if (ctx.channel().localAddress().equals(IdentityPublicKey.of("fe8793d5e8f8a2dcdeada2065b9162596f947619723fe101b8db186b49d01b9b")) && ctx.channel().remoteAddress().equals(IdentityPublicKey.of("de7ffbda07bdeb235fa94f208fdc8c01e3fda67aeedb2cc85958153018e6b5e0"))) {
                    ctx.executor().scheduleAtFixedRate(() -> {
                        ByteBuf msg = ctx.alloc().buffer().writeLong(1234567890123456789L);
                        System.out.println("PING " + ByteBufUtil.hexDump((ByteBuf) msg));
                        ctx.writeAndFlush(msg);
                    }, 1000, 1000, TimeUnit.MILLISECONDS);
                }
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                super.channelRead(ctx, msg);
                if (ctx.channel().localAddress().equals(IdentityPublicKey.of("de7ffbda07bdeb235fa94f208fdc8c01e3fda67aeedb2cc85958153018e6b5e0")) && ctx.channel().remoteAddress().equals(IdentityPublicKey.of("fe8793d5e8f8a2dcdeada2065b9162596f947619723fe101b8db186b49d01b9b"))) {
                    System.out.println("Got: " + ByteBufUtil.hexDump((ByteBuf) msg));
                    ByteBuf response = ctx.alloc().buffer().writeLong(1234567890123456789L);
                    System.out.println("PONG " + ByteBufUtil.hexDump(response));
                    ctx.writeAndFlush(response);
                }
            }
        });
    }
}
