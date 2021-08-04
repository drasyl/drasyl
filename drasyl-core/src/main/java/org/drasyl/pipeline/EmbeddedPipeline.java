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
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
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
import org.drasyl.util.scheduler.DrasylSchedulerUtil.DrasylExecutor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.drasyl.util.RandomUtil.randomString;

/**
 * Embedded {@link Pipeline} implementation, that allows easy testing of {@link Handler}s.
 */
@SuppressWarnings({ "java:S107" })
public class EmbeddedPipeline implements AutoCloseable, Pipeline {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public static final Optional<Object> NULL_MESSAGE = Optional.empty();
    private static final short DEFAULT_HANDLER_RANDOM_SUFFIX_LENGTH = 16;
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedPipeline.class);
    protected final Map<String, AbstractHandlerContext> handlerNames;
    protected final DrasylScheduler dependentScheduler;
    protected final DrasylScheduler independentScheduler;
    protected final DrasylConfig config;
    protected final Identity identity;
    protected final PeersManager peersManager;
    protected final Serialization inboundSerialization;
    protected final Serialization outboundSerialization;
    protected final Semaphore outboundMessagesBuffer;
    private final Subject<AddressedEnvelope<Address, Object>> inboundMessages;
    private final Subject<Event> inboundEvents;
    private final Subject<AddressedEnvelope<Address, Object>> outboundMessages;
    private final DrasylExecutor dependentExecutor;
    private final DrasylExecutor independentExecutor;
    protected AbstractEndHandler head;
    protected AbstractEndHandler tail;

    /**
     * Creates a new embedded pipeline and adds all given handler to it. Handler are added with
     * their simple class name.
     *
     * @param config       the config
     * @param identity     the identity
     * @param peersManager the peers manager
     * @param handlers     the handlers
     */
    @SuppressWarnings("java:S109")
    public EmbeddedPipeline(final DrasylConfig config,
                            final Identity identity,
                            final PeersManager peersManager,
                            final Handler... handlers) {
        this(
                config,
                identity,
                peersManager,
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsInbound()),
                new Serialization(config.getSerializationSerializers(), config.getSerializationsBindingsOutbound()),
                new DrasylExecutor(EmbeddedPipeline.class.getSimpleName() + "-L-", 2, 2),
                new DrasylExecutor(EmbeddedPipeline.class.getSimpleName() + "-H-", 2, 2)
        );
        List.of(handlers).forEach(handler -> addLast(handler.getClass().getSimpleName() + randomString(DEFAULT_HANDLER_RANDOM_SUFFIX_LENGTH), handler));
    }

    private EmbeddedPipeline(final DrasylConfig config,
                             final Identity identity,
                             final PeersManager peersManager,
                             final Serialization inboundSerialization,
                             final Serialization outboundSerialization,
                             final DrasylExecutor dependentExecutor,
                             final DrasylExecutor independentExecutor) {
        this.handlerNames = requireNonNull((Map<String, AbstractHandlerContext>) new ConcurrentHashMap<String, AbstractHandlerContext>());
        this.dependentScheduler = requireNonNull(dependentExecutor.getScheduler());
        this.independentScheduler = requireNonNull(independentExecutor.getScheduler());
        this.config = requireNonNull(config);
        this.identity = requireNonNull(identity);
        this.peersManager = requireNonNull(peersManager);
        this.inboundSerialization = requireNonNull(inboundSerialization);
        this.outboundSerialization = requireNonNull(outboundSerialization);
        this.outboundMessagesBuffer = null;
        this.dependentExecutor = dependentExecutor;
        this.independentExecutor = independentExecutor;
        inboundMessages = ReplaySubject.<AddressedEnvelope<Address, Object>>create().toSerialized();
        inboundEvents = ReplaySubject.<Event>create().toSerialized();
        outboundMessages = ReplaySubject.<AddressedEnvelope<Address, Object>>create().toSerialized();

        this.head = new AbstractEndHandler(HeadContext.DRASYL_HEAD_HANDLER, config, this, dependentExecutor.getScheduler(), independentExecutor.getScheduler(), identity, peersManager, inboundSerialization, outboundSerialization) {
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
        this.tail = new TailContext(inboundEvents::onNext, config, this, dependentExecutor.getScheduler(), independentExecutor.getScheduler(), identity, peersManager, inboundSerialization, outboundSerialization) {
            @Override
            public void onInbound(final HandlerContext ctx,
                                  final Address sender,
                                  final Object msg,
                                  final CompletableFuture<Void> future) {
                if (sender instanceof IdentityPublicKey) {
                    final IdentityPublicKey senderAddress = (IdentityPublicKey) sender;
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

        dependentExecutor.shutdown().join();
        independentExecutor.shutdown().join();

        outboundMessages.toList().blockingGet().forEach(o -> ReferenceCountUtil.safeRelease(o.getContent()));
        inboundMessages.toList().blockingGet().forEach(o -> ReferenceCountUtil.safeRelease(o.getContent()));
    }

    @Override
    public boolean isWritable() {
        return false;
    }

    @Override
    public int messagesBeforeUnwritable() {
        return 0;
    }

    protected Logger log() {
        return LOG;
    }

    @SuppressWarnings("java:S2221")
    protected void initPointer() {
        this.head.setNextHandlerContext(this.tail);
        this.tail.setPrevHandlerContext(this.head);
        try {
            this.head.handler().onAdded(this.head);
            this.tail.handler().onAdded(this.head);
        }
        catch (final Exception e) {
            this.head.passException(e);
        }
    }

    @Override
    public Pipeline addFirst(final String name, final Handler handler) {
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.head);
            newCtx.setNextHandlerContext(this.head.getNext());

            // Set correct pointer on old context
            this.head.getNext().setPrevHandlerContext(newCtx);
            this.head.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    /**
     * Checks if the handler is already registered, if so an {@link IllegalArgumentException} is
     * thrown.
     *
     * @param name handler name
     * @throws IllegalArgumentException if handler is already registered
     */
    private void collisionCheck(final String name) {
        if (handlerNames.containsKey(name)) {
            throw new IllegalArgumentException("A handler with this name is already registered");
        }
    }

    @SuppressWarnings("java:S2221")
    private void registerNewHandler(final String name, final AbstractHandlerContext handlerCtx) {
        // Add to handlerName list
        handlerNames.put(name, handlerCtx);

        // Call handler added
        try {
            handlerCtx.handler().onAdded(handlerCtx);
        }
        catch (final Exception e) {
            handlerCtx.passException(e);
            log().warn("Error on adding handler `{}`: ", handlerCtx::name, () -> e);
        }
    }

    @Override
    public Pipeline addLast(final String name, final Handler handler) {
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(this.tail.getPrev());
            newCtx.setNextHandlerContext(this.tail);

            // Set correct pointer on old context
            this.tail.getPrev().setNextHandlerContext(newCtx);
            this.tail.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addBefore(final String baseName, final String name, final Handler handler) {
        requireNonNull(baseName);
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx.getPrev());
            newCtx.setNextHandlerContext(baseCtx);

            // Set correct pointer on old context
            baseCtx.getPrev().setNextHandlerContext(newCtx);
            baseCtx.setPrevHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline addAfter(final String baseName, final String name, final Handler handler) {
        requireNonNull(baseName);
        requireNonNull(name);
        requireNonNull(handler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            collisionCheck(name);

            final AbstractHandlerContext baseCtx = handlerNames.get(baseName);
            requireNonNull(baseCtx);

            newCtx = new DefaultHandlerContext(name, handler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(baseCtx);
            newCtx.setNextHandlerContext(baseCtx.getNext());

            // Set correct pointer on old context
            baseCtx.getNext().setPrevHandlerContext(newCtx);
            baseCtx.setNextHandlerContext(newCtx);

            registerNewHandler(name, newCtx);
        }

        return this;
    }

    @Override
    public Pipeline remove(final String name) {
        requireNonNull(name);

        synchronized (this) {
            final AbstractHandlerContext ctx = handlerNames.remove(name);
            if (ctx == null) {
                throw new NoSuchElementException("There is no handler with this name in the pipeline");
            }

            // call remove action
            removeHandlerAction(ctx);

            final AbstractHandlerContext prev = ctx.getPrev();
            final AbstractHandlerContext next = ctx.getNext();
            prev.setNextHandlerContext(next);
            next.setPrevHandlerContext(prev);
        }

        return this;
    }

    @SuppressWarnings("java:S2221")
    private void removeHandlerAction(final AbstractHandlerContext ctx) {
        // call remove action
        try {
            ctx.handler().onRemoved(ctx);
        }
        catch (final Exception e) {
            ctx.passException(e);
            log().warn("Error on adding handler `{}`: ", ctx::name, () -> e);
        }
    }

    @Override
    public Pipeline replace(final String oldName, final String newName, final Handler newHandler) {
        requireNonNull(oldName);
        requireNonNull(newName);
        requireNonNull(newHandler);
        final AbstractHandlerContext newCtx;

        synchronized (this) {
            if (!oldName.equals(newName)) {
                collisionCheck(newName);
            }

            final AbstractHandlerContext oldCtx = handlerNames.remove(oldName);
            final AbstractHandlerContext prev = oldCtx.getPrev();
            final AbstractHandlerContext next = oldCtx.getNext();

            // call remove action
            removeHandlerAction(oldCtx);

            newCtx = new DefaultHandlerContext(newName, newHandler, config, this, dependentScheduler, independentScheduler, identity, peersManager, inboundSerialization, outboundSerialization);
            // Set correct pointer on new context
            newCtx.setPrevHandlerContext(prev);
            newCtx.setNextHandlerContext(next);

            // Set correct pointer on old context
            prev.setNextHandlerContext(newCtx);
            next.setPrevHandlerContext(newCtx);

            registerNewHandler(newName, newCtx);
        }

        return this;
    }

    @Override
    public Handler get(final String name) {
        requireNonNull(name);

        if (handlerNames.containsKey(name)) {
            return handlerNames.get(name).handler();
        }

        return null;
    }

    @Override
    public HandlerContext context(final String name) {
        requireNonNull(name);

        return handlerNames.get(name);
    }

    @Override
    public CompletableFuture<Void> processInbound(final Address sender,
                                                  final Object msg) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.head.passInbound(sender, msg, rtn);

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processInbound(final Event event) {
        final CompletableFuture<Void> rtn = new CompletableFuture<>();

        this.head.passEvent(event, rtn);

        return rtn;
    }

    @Override
    public CompletableFuture<Void> processOutbound(final Address recipient,
                                                   final Object msg) {
        if (outboundMessagesBuffer == null) {
            final CompletableFuture<Void> rtn = new CompletableFuture<>();
            this.tail.passOutbound(recipient, msg, rtn);
            return rtn;
        }
        else if (outboundMessagesBuffer.tryAcquire()) {
            final CompletableFuture<Void> rtn = new CompletableFuture<>();
            rtn.whenComplete((unused, e) -> outboundMessagesBuffer.release());
            this.tail.passOutbound(recipient, msg, rtn);
            return rtn;
        }
        else {
            return failedFuture(new Exception("Outbound messages buffer capacity exceeded. New messages can only be enqueued once the previous ones have been processed. Alternatively drasyl.message.buffer-size can be increased."));
        }
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
