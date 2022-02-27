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
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.plugin.groups.client.Group;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * This message is sent by the groups server to the client when the join to a group was successful.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupWelcomeMessage extends GroupsServerMessage {
    public static GroupWelcomeMessage of(final Group group,
                                         final Set<IdentityPublicKey> members) {
        return new AutoValue_GroupWelcomeMessage(group, members);
    }

    public static GroupWelcomeMessage of(final ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < 3 + IdentityPublicKey.KEY_LENGTH_AS_BYTES) {
            throw new IllegalArgumentException("not enough bytes.");
        }
        final int lenGroup = byteBuf.readUnsignedShort();
        final Group group = Group.of(byteBuf.readCharSequence(lenGroup, StandardCharsets.UTF_8).toString());
        final Set<IdentityPublicKey> members = new HashSet<>();
        while (byteBuf.readableBytes() != 0 && byteBuf.readableBytes() % 32 == 0) {
            final byte[] id = new byte[IdentityPublicKey.KEY_LENGTH_AS_BYTES];
            byteBuf.readBytes(id);
            members.add(IdentityPublicKey.of(id));
        }

        return of(group, members);
    }

    public abstract Set<IdentityPublicKey> getMembers();

    @Override
    public void writeTo(final ByteBuf out) {
        super.writeTo(out);
        for (final IdentityPublicKey id : getMembers()) {
            out.writeBytes(id.toByteArray());
        }
    }
}
