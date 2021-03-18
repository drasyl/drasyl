/*
 * Copyright (c) 2020-2021.
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
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.pipeline.serialization.Serialization;
import org.drasyl.util.ReferenceCountUtil;
import org.drasyl.util.TypeReference;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.drasyl.util.scheduler.DrasylSchedulerUtil;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.drasyl.util.RandomUtil.randomString;

/**
 * Embedded {@link Pipeline} implementation, that allows easy testing of {@link Handler}s.
 */
@SuppressWarnings({ "java:S107" })
public class EmbeddedPipeline extends AbstractPipeline implements AutoCloseable {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static final Optional<Object> NULL_MESSAGE = Optional.empty();
    private static final short DEFAULT_HANDLER_RANDOM_SUFFIX_LENGTH = 16;
    private final Subject<AddressedEnvelope<Address, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<Object> outboundMessages;

    public EmbeddedPipeline(final DrasylConfig config,
                            final Identity identity,
                            final PeersManager peersManager,
                            final DrasylScheduler dependentScheduler,
                            final DrasylScheduler independentScheduler,
                            final Handler... handlers) {
        this(config, identity, peersManager, new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()), new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()), dependentScheduler, independentScheduler);
        List.of(handlers).forEach(handler -> addLast(handler.getClass().getSimpleName() + randomString(DEFAULT_HANDLER_RANDOM_SUFFIX_LENGTH), handler));
    }

    /**
     * Creates a new embedded pipeline and adds all given handler to it. Handler are added with
     * their simple class name.
     *
     * @param config       the config
     * @param identity     the identity
     * @param peersManager the peers manager
     * @param handlers     the handlers
     */
    public EmbeddedPipeline(final DrasylConfig config,
                            final Identity identity,
                            final PeersManager peersManager,
                            final Handler... handlers) {
        this(config, identity, peersManager, DrasylSchedulerUtil.getInstanceLight(), DrasylSchedulerUtil.getInstanceHeavy(), handlers);
    }

    private EmbeddedPipeline(final DrasylConfig config,
                             final Identity identity,
                             final PeersManager peersManager,
                             final Serialization inboundSerialization,
                             final Serialization outboundSerialization,
                             final DrasylScheduler dependentScheduler,
                             final DrasylScheduler independentScheduler) {
        super(new ConcurrentHashMap<>(), dependentScheduler, independentScheduler, config, identity, peersManager, inboundSerialization, outboundSerialization);
        this.inboundMessages = ReplaySubject.<AddressedEnvelope<Address, Object>>create().toSerialized();
        inboundEvents = ReplaySubject.<Event>create().toSerialized();
        outboundMessages = ReplaySubject.create().toSerialized();

        this.head = new AbstractEndHandler(HeadContext.DRASYL_HEAD_HANDLER, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public void onOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
                if (msg == null) {
                    outboundMessages.onNext(NULL_MESSAGE);
                }
                else {
                    outboundMessages.onNext(msg);
                }

                future.complete(null);
            }
        };
        this.tail = new TailContext(inboundEvents::onNext, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public void onInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) {
                if (sender instanceof CompressedPublicKey) {
                    final CompressedPublicKey senderAddress = (CompressedPublicKey) sender;
                    inboundEvents.onNext(MessageEvent.of(senderAddress, msg));
                }
                inboundMessages.onNext(new DefaultAddressedEnvelope<>(sender, null, msg));

                future.complete(null);
            }
        };

        initPointer();
    }

    /**
     * @return all messages of type {@code T} that passes the pipeline until the end
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<T> inboundMessages(final Class<T> clazz) {
        return (Observable<T>) inboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE).filter(clazz::isInstance);
    }

    /**
     * @return all messages of type {@code T} that passes the pipeline until the end
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<T> inboundMessages(final TypeReference<T> typeReference) {
        return (Observable<T>) inboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE).filter(m -> isInstance(typeReference.getType(), m));
    }

    public Observable<Object> inboundMessages() {
        return inboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE);
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<AddressedEnvelope<Address, Object>> inboundMessagesWithRecipient() {
        return inboundMessages;
    }

    /**
     * @return all events that passes the pipeline until the end
     */
    public Observable<Event> inboundEvents() {
        return inboundEvents;
    }

    /**
     * @return all messages of type {@code T} that passes the pipeline until the end
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<T> outboundMessages(final Class<T> clazz) {
        return (Observable<T>) outboundMessages.filter(clazz::isInstance);
    }

    /**
     * @return all messages of type {@code T} that passes the pipeline until the end
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<T> outboundMessages(final TypeReference<T> typeReference) {
        return (Observable<T>) outboundMessages.filter(m -> isInstance(typeReference.getType(), m));
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Object> outboundMessages() {
        return outboundMessages;
    }

    /**
     * This method does release all potentially acquired {@link io.netty.util.ReferenceCounted}
     * objects.
     */
    @Override
    public void close() {
        outboundMessages.onComplete();
        inboundMessages.onComplete();
        inboundEvents.onComplete();

        // remove all handler from pipeline
        for (final String ctx : new HashMap<>(handlerNames).keySet()) {
            this.remove(ctx);
        }
        outboundMessages.toList().blockingGet().forEach(ReferenceCountUtil::safeRelease);
        inboundMessages.toList().blockingGet().forEach(ReferenceCountUtil::safeRelease);
    }

    private static boolean isInstance(final Type type, final Object obj) {
        if (type instanceof Class<?>) {
            return ((Class<?>) type).isInstance(obj);
        }
        else if (type instanceof ParameterizedType) {
            final Type rawType = ((ParameterizedType) type).getRawType();
            return isInstance(rawType, obj);
        }
        return false;
    }
}
