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
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;
import org.drasyl.util.Pair;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embedded {@link Pipeline} implementation, that allows easy testing of {@link Handler}s.
 */
@SuppressWarnings({ "java:S107" })
public class EmbeddedPipeline extends DefaultPipeline {
    private final Subject<Pair<CompressedPublicKey, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<ApplicationMessage> outboundMessages;

    /**
     * Creates a new embedded pipeline and adds all given handler to it. Handler are added with
     * their simple class name.
     *
     * @param handlers the handlers
     */
    public EmbeddedPipeline(Identity identity,
                            TypeValidator validator,
                            Handler... handlers) {
        this(identity, validator);
        List.of(handlers).forEach(handler -> addLast(handler.getClass().getSimpleName(), handler));
    }

    public EmbeddedPipeline(Identity identity, TypeValidator validator) {
        inboundMessages = ReplaySubject.create();
        inboundEvents = ReplaySubject.create();
        outboundMessages = ReplaySubject.create();

        this.handlerNames = new ConcurrentHashMap<>();
        this.head = new HeadContext(outboundMessages::onNext, config, this, DrasylScheduler.getInstanceHeavy(), identity, validator);
        this.tail = new TailContext(inboundEvents::onNext, config, this, DrasylScheduler.getInstanceHeavy(), identity, validator) {
            @Override
            public void read(HandlerContext ctx, CompressedPublicKey sender, Object msg) {
                // Pass message to Application
                inboundEvents.onNext(new MessageEvent(Pair.of(sender, msg)));
                inboundMessages.onNext(Pair.of(sender, msg));
            }
        };
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.identity = identity;
        this.validator = validator;

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
    public Observable<ApplicationMessage> outboundMessages() {
        return outboundMessages;
    }
}
