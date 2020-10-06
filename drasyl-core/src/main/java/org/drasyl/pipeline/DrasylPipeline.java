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
import org.drasyl.event.Event;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.codec.ApplicationMessage2ObjectHolderHandler;
import org.drasyl.pipeline.codec.DefaultCodec;
import org.drasyl.pipeline.codec.ObjectHolder2ApplicationMessageHandler;
import org.drasyl.pipeline.codec.TypeValidator;
import org.drasyl.util.DrasylScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The default {@link Pipeline} implementation. Used to implement plugins for drasyl.
 */
public class DrasylPipeline extends DefaultPipeline {
    public DrasylPipeline(final Consumer<Event> eventConsumer,
                          final DrasylConfig config,
                          final Identity identity) {
        this.handlerNames = new ConcurrentHashMap<>();
        this.inboundValidator = TypeValidator.ofInboundValidator(config);
        this.outboundValidator = TypeValidator.ofOutboundValidator(config);
        this.head = new HeadContext(config, this, DrasylScheduler.getInstanceHeavy(), identity, inboundValidator, outboundValidator);
        this.tail = new TailContext(eventConsumer, config, this, DrasylScheduler.getInstanceHeavy(), identity, inboundValidator, outboundValidator);
        this.scheduler = DrasylScheduler.getInstanceLight();
        this.config = config;
        this.identity = identity;

        initPointer();

        // add default codec
        addFirst(DefaultCodec.DEFAULT_CODEC, DefaultCodec.INSTANCE);
        addFirst(ApplicationMessage2ObjectHolderHandler.APP_MSG2OBJECT_HOLDER, ApplicationMessage2ObjectHolderHandler.INSTANCE);
        addFirst(ObjectHolder2ApplicationMessageHandler.OBJECT_HOLDER2APP_MSG, ObjectHolder2ApplicationMessageHandler.INSTANCE);
    }

    DrasylPipeline(final Map<String, AbstractHandlerContext> handlerNames,
                   final AbstractEndHandler head,
                   final AbstractEndHandler tail,
                   final Scheduler scheduler,
                   final DrasylConfig config,
                   final Identity identity) {
        this.handlerNames = handlerNames;
        this.head = head;
        this.tail = tail;
        this.scheduler = scheduler;
        this.config = config;
        this.identity = identity;
    }
}