/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.peer.connection.superpeer;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.Message;

import java.util.concurrent.CompletableFuture;

/**
 * This handler catches all in- and outbound messages and saves it inside a static variable.
 * <p>
 * This handler also allows to inject messages directly into the pipeline. This handler only works
 * if there is only one dummy server at the moment.
 * <p>
 * <b>This handler is NOT thread safe! This handler should ONLY be used for testing!</b>
 */
public class IntegrationTestHandler extends SimpleChannelDuplexHandler<Message, Message> {
    private static final Subject<Message> OUTBOUND_MESSAGES = PublishSubject.create();
    private static final Subject<Message> INBOUND_MESSAGES = PublishSubject.create();
    private static ChannelHandlerContext channelHandlerContext;
    private static CompletableFuture<Void> readyToSend = new CompletableFuture<>();

    public IntegrationTestHandler() {
        readyToSend = new CompletableFuture<>();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        channelHandlerContext = ctx;
        if (!readyToSend.isDone()) {
            readyToSend.complete(null);
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Message msg) {
        INBOUND_MESSAGES.onNext(msg);
        ctx.fireChannelRead(msg);
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx,
                                 Message msg, ChannelPromise promise) {
        OUTBOUND_MESSAGES.onNext(msg);
        ctx.write(msg);
    }

    public static Observable<Message> receivedMessages() {
        return INBOUND_MESSAGES;
    }

    public static Observable<Message> sentMessages() {
        return OUTBOUND_MESSAGES;
    }

    public static void injectMessage(Message message) {
        readyToSend.join();
        channelHandlerContext.writeAndFlush(message);
    }
}
