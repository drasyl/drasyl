/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.pubsub;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;

import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * Published {@link #content()} ()} to topic {@link #getTopic()}.
 *
 * @see PubSubPublished
 */
public final class PubSubPublish extends DefaultByteBufHolder implements PubSubMessage {
    private final UUID id;
    private final String topic;

    private PubSubPublish(final UUID id, final String topic, final ByteBuf content) {
        super(content);
        this.id = requireNonNull(id);
        this.topic = requireNonNull(topic);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public ByteBuf getContent() {
        return content();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final PubSubPublish that = (PubSubPublish) o;
        return Objects.equals(id, that.id) && Objects.equals(topic, that.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, topic);
    }

    static PubSubPublish of(final UUID id, final String topic, final ByteBuf content) {
        return new PubSubPublish(id, topic, content);
    }

    public static PubSubPublish of(final String topic, final ByteBuf content) {
        return of(UUID.randomUUID(), topic, content);
    }
}
