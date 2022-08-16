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
package org.drasyl.peer.connection.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.peer.connection.handler.SimpleChannelDuplexHandler;
import org.drasyl.peer.connection.message.Message;

public class TestServerChannelInitializer extends DefaultServerChannelInitializer {
    private final PublishSubject<Message> sentMessages;
    private final PublishSubject<Message> receivedMessages;

    public TestServerChannelInitializer(final ServerEnvironment environment) {
        super(environment);
        sentMessages = PublishSubject.create();
        receivedMessages = PublishSubject.create();
    }

    public PublishSubject<Message> sentMessages() {
        return sentMessages;
    }

    public PublishSubject<Message> receivedMessages() {
        return receivedMessages;
    }

    @Override
    protected void afterPojoMarshalStage(final ChannelPipeline pipeline) {
        super.afterPojoMarshalStage(pipeline);
        pipeline.addLast(new SimpleChannelDuplexHandler<Message, Message>(false, false, false) {
            @Override
            protected void channelRead0(final ChannelHandlerContext ctx,
                                        final Message msg) {
                receivedMessages.onNext(msg);
                ctx.fireChannelRead(msg);
            }

            @Override
            protected void channelWrite0(final ChannelHandlerContext ctx,
                                         final Message msg,
                                         final ChannelPromise promise) {
                sentMessages.onNext(msg);
                ctx.write(msg, promise);
            }

            @Override
            public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
                sentMessages.onComplete();
                receivedMessages.onComplete();
                super.channelUnregistered(ctx);
            }
        });
    }
}