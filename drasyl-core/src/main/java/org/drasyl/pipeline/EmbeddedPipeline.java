/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
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
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
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
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPipeline.class);
    private final Subject<AddressedEnvelope<Address, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<AddressedEnvelope<Address, Object>> outboundMessages;

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
        super(new ConcurrentHashMap<>(), dependentScheduler, independentScheduler, config, identity, peersManager, inboundSerialization, outboundSerialization, null);
        this.inboundMessages = ReplaySubject.<AddressedEnvelope<Address, Object>>create().toSerialized();
        inboundEvents = ReplaySubject.<Event>create().toSerialized();
        outboundMessages = ReplaySubject.<AddressedEnvelope<Address, Object>>create().toSerialized();

        this.head = new AbstractEndHandler(HeadContext.DRASYL_HEAD_HANDLER, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            protected Logger log() {
                return LOG;
            }

            @Override
            public void onOutbound(final HandlerContext ctx,
                                   final Address recipient,
                                   final Object msg,
                                   final CompletableFuture<Void> future) {
                outboundMessages.onNext(new DefaultAddressedEnvelope<>(null, recipient, msg));

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
    public Observable<AddressedEnvelope<Address, Object>> inboundMessagesWithSender() {
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
        return (Observable<T>) outboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE).filter(clazz::isInstance);
    }

    /**
     * @return all messages of type {@code T} that passes the pipeline until the end
     */
    @SuppressWarnings("unchecked")
    public <T> Observable<T> outboundMessages(final TypeReference<T> typeReference) {
        return (Observable<T>) outboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE).filter(m -> isInstance(typeReference.getType(), m));
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<Object> outboundMessages() {
        return outboundMessages.map(m -> m.getContent() != null ? m.getContent() : NULL_MESSAGE);
    }

    /**
     * @return all messages that passes the pipeline until the end
     */
    public Observable<AddressedEnvelope<Address, Object>> outboundMessagesWithRecipient() {
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

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public int messagesBeforeUnwritable() {
        return 0;
    }

    @Override
    protected Logger log() {
        return LOG;
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
