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

/**
 * This message is send by the groups client to the server to leave a group.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupLeaveMessage extends GroupsClientMessage {
    public static GroupLeaveMessage of(final Group group) {
        return new AutoValue_GroupLeaveMessage(group);
    }

    public static GroupLeaveMessage of(final ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < 3) {
            throw new IllegalArgumentException("bytebuf is to short.");
        }

        final int lenGroup = byteBuf.readUnsignedShort();
        final Group group = Group.of(byteBuf.readCharSequence(lenGroup, StandardCharsets.UTF_8).toString());

        return of(group);
    }
}
