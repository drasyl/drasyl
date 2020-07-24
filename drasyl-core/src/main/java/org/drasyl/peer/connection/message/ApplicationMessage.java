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
package org.drasyl.peer.connection.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.drasyl.identity.CompressedPublicKey;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * A message that is sent by an application running on drasyl.
 */
public class ApplicationMessage extends RelayableMessage implements RequestMessage {
    protected final CompressedPublicKey sender;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    protected final Map<String, String> headers;
    protected final byte[] payload;
    protected final Class<?> payloadClazz;

    public ApplicationMessage(MessageId id,
                              CompressedPublicKey sender,
                              CompressedPublicKey recipient,
                              byte[] payload,
                              Class<?> payloadClazz,
                              short hopCount) {
        this(id, sender, recipient, Map.of(), payload, payloadClazz, hopCount);
    }

    @JsonCreator
    public ApplicationMessage(@JsonProperty("id") MessageId id,
                              @JsonProperty("sender") CompressedPublicKey sender,
                              @JsonProperty("recipient") CompressedPublicKey recipient,
                              @JsonProperty("headers") Map<String, String> headers,
                              @JsonProperty("payload") byte[] payload,
                              @JsonProperty("payloadClazz") Class<?> payloadClazz,
                              @JsonProperty("hopCount") short hopCount) {
        super(id, recipient, hopCount);
        this.sender = requireNonNull(sender);
        if (headers != null) {
            this.headers = Map.copyOf(headers);
        }
        else {
            // needed for backward compatibility
            this.headers = Map.of();
        }
        this.payload = requireNonNull(payload);

        /*
         * Needed for backwards compatible with {@link ApplicationMessage} of versions lower
         * than 0.1.3-SNAPSHOT and returns on empty {@link #payloadClazz} the {@code byte[].class}.
         */
        this.payloadClazz = Objects.requireNonNullElse(payloadClazz, byte[].class);
    }

    /**
     * Creates a new message.
     *
     * @param sender    The sender
     * @param recipient The recipient
     * @param payload   The data to be sent
     */
    public ApplicationMessage(CompressedPublicKey sender,
                              CompressedPublicKey recipient,
                              Map<String, String> headers,
                              byte[] payload,
                              Class<?> payloadClazz) {
        this(sender, recipient, headers, payload, payloadClazz, (short) 0);
    }

    ApplicationMessage(CompressedPublicKey sender,
                       CompressedPublicKey recipient,
                       Map<String, String> headers,
                       byte[] payload,
                       Class<?> payloadClazz,
                       short hopCount) {
        super(recipient, hopCount);
        this.sender = requireNonNull(sender);
        this.headers = requireNonNull(headers);
        this.payload = requireNonNull(payload);

        /*
         * Needed for backwards compatible with {@link ApplicationMessage} of versions lower
         * than 0.1.3-SNAPSHOT and returns on empty {@link #payloadClazz} the {@code byte[].class}.
         */
        this.payloadClazz = Objects.requireNonNullElse(payloadClazz, byte[].class);
    }

    /**
     * Creates a new message.
     *
     * @param sender    The sender
     * @param recipient The recipient
     * @param payload   The data to be sent
     */
    public ApplicationMessage(CompressedPublicKey sender,
                              CompressedPublicKey recipient,

                              byte[] payload,
                              Class<?> payloadClazz) {
        this(sender, recipient, Map.of(), payload, payloadClazz, (short) 0);
    }

    /**
     * @return the class of the encoded payload
     * @since 0.1.3-SNAPSHOT
     */
    public Class<?> getPayloadClazz() {
        return payloadClazz;
    }

    public CompressedPublicKey getSender() {
        return sender;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Returns the value of header with name <code>name</code>, or {@code null} if this header does
     * not exist.
     *
     * @return value of header with name <code>name</code>, or {@code null} if this header does not
     * exist
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    public byte[] getPayload() {
        return payload;
    }

    /**
     * @return a ByteBuf that wraps the underling payload byte array
     */
    public ByteBuf payloadAsByteBuf() {
        return Unpooled.wrappedBuffer(payload);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(super.hashCode(), sender);
        result = 31 * result + Arrays.hashCode(payload);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ApplicationMessage that = (ApplicationMessage) o;
        return Objects.equals(sender, that.sender) &&
                Arrays.equals(payload, that.payload);
    }

    @Override
    public String toString() {
        return "ApplicationMessage{" +
                "sender=" + sender +
                ", payload=byte[" + Optional.ofNullable(payload).orElse(new byte[]{}).length + "] { ... }" +
                ", payloadClazz=" + payloadClazz +
                ", recipient=" + recipient +
                ", hopCount=" + hopCount +
                ", id='" + id + '\'' +
                '}';
    }
}
