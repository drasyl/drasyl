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
import org.drasyl.identity.ProofOfWork;
import org.drasyl.node.plugin.groups.client.Group;
import org.drasyl.util.UnsignedShort;

import java.nio.charset.StandardCharsets;

import static org.drasyl.util.Preconditions.requireInRange;

/**
 * This message is send by the groups client to the server to join a group.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class GroupJoinMessage extends GroupsClientMessage {
    public static final int MIN_LENGTH = 11;

    public static GroupJoinMessage of(final Group group,
                                      final String credentials,
                                      final ProofOfWork proofOfWork,
                                      final boolean renew) {
        requireInRange(credentials.length(), UnsignedShort.MIN_VALUE.getValue(), UnsignedShort.MAX_VALUE.getValue());
        return new AutoValue_GroupJoinMessage(group, renew, credentials, proofOfWork);
    }

    public static GroupJoinMessage of(final ByteBuf byteBuf) {
        if (byteBuf.readableBytes() < MIN_LENGTH) {
            throw new IllegalArgumentException("bytebuf is to short.");
        }

        final int lenGroup = byteBuf.readUnsignedShort();
        final Group group = Group.of(byteBuf.readCharSequence(lenGroup, StandardCharsets.UTF_8).toString());
        final boolean renew = byteBuf.readBoolean();
        final int lenCred = byteBuf.readUnsignedShort();
        final String cred = byteBuf.readCharSequence(lenCred, StandardCharsets.UTF_8).toString();
        final ProofOfWork pow = ProofOfWork.of(byteBuf.readInt());

        return of(group, cred, pow, renew);
    }

    public abstract boolean isRenew();

    public abstract String getCredentials();

    public abstract ProofOfWork getProofOfWork();

    @Override
    public void writeTo(final ByteBuf out) {
        super.writeTo(out);

        out.writeBoolean(isRenew());
        out.writeBytes(UnsignedShort.of(getCredentials().length()).toBytes());
        out.writeCharSequence(getCredentials(), StandardCharsets.UTF_8);
        out.writeInt(getProofOfWork().getNonce());
    }
}
