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
package org.drasyl.plugin.groups.client.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.plugin.groups.client.Group;

import java.util.Objects;

import static java.util.Objects.requireNonNull;
import static org.drasyl.util.SecretUtil.maskSecret;

/**
 * This message is send by the groups client to the server to join a group.
 * <p>
 * This is an immutable object.
 */
public class GroupJoinMessage extends GroupActionMessage implements GroupsClientMessage {
    /*
     * FIXME: This secret is transmitted in plain text as long as
     * drasyl does not support end-to-end encryption for all messages.
     */
    private final String credentials;
    private final ProofOfWork proofOfWork;
    private final boolean renew;

    @JsonCreator
    public GroupJoinMessage(@JsonProperty("group") final Group group,
                            @JsonProperty("credentials") final String credentials,
                            @JsonProperty("proofOfWork") final ProofOfWork proofOfWork,
                            @JsonProperty("renew") final boolean renew) {
        super(group);
        this.credentials = requireNonNull(credentials);
        this.proofOfWork = requireNonNull(proofOfWork);
        this.renew = renew;
    }

    public boolean isRenew() {
        return renew;
    }

    public String getCredentials() {
        return credentials;
    }

    public ProofOfWork getProofOfWork() {
        return proofOfWork;
    }

    @Override
    public String toString() {
        return "GroupJoinMessage{" +
                "credentials='" + maskSecret(credentials) + '\'' +
                ", group='" + group + '\'' +
                ", proofOfWork=" + proofOfWork + '\'' +
                ", renew=" + renew +
                '}';
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
        final GroupJoinMessage that = (GroupJoinMessage) o;
        return Objects.equals(credentials, that.credentials) &&
                Objects.equals(proofOfWork, that.proofOfWork) &&
                Objects.equals(renew, that.renew);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), credentials, proofOfWork, renew);
    }
}
