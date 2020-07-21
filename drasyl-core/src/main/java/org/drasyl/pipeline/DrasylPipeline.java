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

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylConsumer;
import org.drasyl.util.DrasylScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline extends DefaultPipeline {
    public DrasylPipeline(Consumer<Event> eventConsumer,
                          DrasylConsumer<ApplicationMessage, DrasylException> outboundConsumer,
                          DrasylConfig config,
                          Identity identity) {
        this.handlerNames = new ConcurrentHashMap<>();
        this.validator = TypeValidator.of(config);
        this.head = new HeadContext(outboundConsumer, config, this, DrasylScheduler.getInstanceHeavy(), identity, validator);
        this.tail = new TailContext(eventConsumer, config, this, DrasylScheduler.getInstanceHeavy(), identity, validator);
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.config = config;
        this.identity = identity;

        initPointer();

        // add default codec
        addFirst(DefaultCodec.DEFAULT_CODEC, DefaultCodec.INSTANCE);
    }

    DrasylPipeline(Map<String, AbstractHandlerContext> handlerNames,
                   AbstractHandlerContext head,
                   AbstractHandlerContext tail,
                   Scheduler scheduler,
                   DrasylConfig config,
                   Identity identity) {
        this.handlerNames = handlerNames;
        this.head = head;
        this.tail = tail;
        this.scheduler = scheduler;
        this.config = config;
        this.identity = identity;
    }
}
