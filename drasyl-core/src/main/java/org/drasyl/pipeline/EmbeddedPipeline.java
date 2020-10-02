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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.crypto.Crypto;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.Pair;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded {@link Pipeline} implementation, that allows easy testing of {@link Handler}s.
 */
@SuppressWarnings({ "java:S107" })
public class EmbeddedPipeline extends DefaultPipeline {
    private final Subject<Pair<CompressedPublicKey, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<Object> outboundMessages;

    /**
     * Creates a new embedded pipeline and adds all given handler to it. Handler are added with
     * their simple class name.
     *
     * @param identity          the identity
     * @param inboundValidator  the inbound validator
     * @param outboundValidator the outbound validator
     * @param handlers          the handlers
     */
    public EmbeddedPipeline(final Identity identity,
                            final TypeValidator inboundValidator,
                            final TypeValidator outboundValidator,
                            final Handler... handlers) {
        this(identity, inboundValidator, outboundValidator);
        List.of(handlers).forEach(handler -> addLast(handler.getClass().getSimpleName() + Crypto.randomString(8), handler));
    }

    public EmbeddedPipeline(final Identity identity,
                            final TypeValidator inboundValidator,
                            final TypeValidator outboundValidator) {
        inboundMessages = ReplaySubject.create();
        inboundEvents = ReplaySubject.create();
        outboundMessages = ReplaySubject.create();

        this.handlerNames = new ConcurrentHashMap<>();
        this.head = new AbstractEndHandler(HeadContext.DRASYL_HEAD_HANDLER, config, this, DrasylScheduler.getInstanceHeavy(), identity, inboundValidator, outboundValidator) {
            @Override
            public void write(final HandlerContext ctx,
                              final CompressedPublicKey recipient,
                              final Object msg,
                              final CompletableFuture<Void> future) {
                outboundMessages.onNext(msg);
                future.complete(null);
            }
        };
        this.tail = new TailContext(inboundEvents::onNext, config, this, DrasylScheduler.getInstanceHeavy(), identity, inboundValidator, outboundValidator) {
            @Override
            public void read(final HandlerContext ctx,
                             final CompressedPublicKey sender,
                             final Object msg,
                             final CompletableFuture<Void> future) {
                inboundEvents.onNext(new MessageEvent(sender, msg));
                inboundMessages.onNext(Pair.of(sender, msg));
                future.complete(null);
            }
        };
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.identity = identity;
        this.inboundValidator = inboundValidator;
        this.outboundValidator = outboundValidator;

        initPointer();
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Pair<CompressedPublicKey, Object>> inboundMessages() {
        return inboundMessages;
    }

    /**
     * @return all events that passes the pipeline until the end
     */
    public Observable<Event> inboundEvents() {
        return inboundEvents;
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public <T> Observable<T> outboundMessages(final Class<T> clazz) {
        return (Observable<T>) outboundMessages.filter(clazz::isInstance);
    }

    /**
     * Processes an inbound message by the pipeline.
     *
     * @param sender the sender of the message
     * @param msg    the inbound message
     */
    public CompletableFuture<Void> processInbound(final CompressedPublicKey sender,
                                                  final Object msg) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.scheduler.scheduleDirect(() -> this.head.fireRead(sender, msg, rtn));

        return rtn;
    }
}