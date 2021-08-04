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
import org.drasyl.event.Event;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.util.TypeReference;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface EmbeddedPipeline extends AutoCloseable, Pipeline {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    Optional<Object> NULL_MESSAGE = Optional.empty();

    @SuppressWarnings("unchecked")
    <T> Observable<T> inboundMessages(Class<T> clazz);

    @SuppressWarnings("unchecked")
    <T> Observable<T> inboundMessages(TypeReference<T> typeReference);

    Observable<Object> inboundMessages();

    Observable<AddressedEnvelope<Address, Object>> inboundMessagesWithSender();

    Observable<Event> inboundEvents();

    @SuppressWarnings("unchecked")
    <T> Observable<T> outboundMessages(Class<T> clazz);

    @SuppressWarnings("unchecked")
    <T> Observable<T> outboundMessages(TypeReference<T> typeReference);

    Observable<Object> outboundMessages();

    Observable<AddressedEnvelope<Address, Object>> outboundMessagesWithRecipient();

    void close();

    boolean isWritable();

    int messagesBeforeUnwritable();

    Pipeline addFirst(String name, Handler handler);

    Pipeline addLast(String name, Handler handler);

    Pipeline addBefore(String baseName, String name, Handler handler);

    Pipeline addAfter(String baseName, String name, Handler handler);

    Pipeline remove(String name);

    Pipeline replace(String oldName, String newName, Handler newHandler);

    Handler get(String name);

    HandlerContext context(String name);

    CompletableFuture<Void> processInbound(Address sender,
                                           Object msg);

    CompletableFuture<Void> processInbound(Event event);

    CompletableFuture<Void> processOutbound(Address recipient,
                                            Object msg);
}
