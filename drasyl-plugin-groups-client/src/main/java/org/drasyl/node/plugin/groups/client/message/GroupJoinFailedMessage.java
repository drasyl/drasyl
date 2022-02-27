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
package org.drasyl.node.plugin.groups.client.message;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import org.drasyl.node.plugin.groups.client.Group;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * This message is sent by the groups server to the client when the join to a group was not
 * successful.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupJoinFailedMessage extends GroupsServerMessage {
    public static GroupJoinFailedMessage of(final Group group,
                                            final Error reason) {
        return new AutoValue_GroupJoinFailedMessage(group, reason);
    }

    public static GroupJoinFailedMessage of(final ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < 4) {
            throw new IllegalArgumentException("bytebuf is to short.");
        }

        final int lenGroup = byteBuf.readUnsignedShort();
        final Group group = Group.of(byteBuf.readCharSequence(lenGroup, StandardCharsets.UTF_8).toString());
        final Error error = Error.from(byteBuf.readByte());

        return of(group, error);
    }

    public abstract Error getReason();

    @Override
    public void writeTo(final ByteBuf out) {
        super.writeTo(out);
        out.writeByte(getReason().ordinal());
    }

    /**
     * Specifies the reason of the {@link GroupJoinFailedMessage}.
     */
    public enum Error {
        ERROR_PROOF_TO_WEAK("The given proof of work is to weak for this group."),
        ERROR_UNKNOWN("An unknown error is occurred during join."),
        ERROR_GROUP_NOT_FOUND("The given group was not found.");
        private static final Map<Integer, Error> errors = new HashMap<>();

        static {
            for (final Error description : values()) {
                errors.put(description.ordinal(), description);
            }
        }

        private final String description;

        Error(final String description) {
            this.description = description;
        }

        /**
         * @return a human readable representation of the reason.
         */
        public String getDescription() {
            return description;
        }

        public static Error from(final int description) {
            return errors.get(description);
        }
    }
}
