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
package org.drasyl.handler.discovery;

import com.google.auto.value.AutoValue;
import org.drasyl.identity.IdentityPublicKey;

/**
 * Signals that we are no longer registered at as a children at {@link
 * RemoveSuperPeerAndPathEvent#getAddress()}  as the direct routing path to that peer is no longer
 * available.
 */
@SuppressWarnings({ "java:S118", "java:S1118", "java:S2974" })
@AutoValue
public abstract class RemoveSuperPeerAndPathEvent implements PathEvent {
    public static RemoveSuperPeerAndPathEvent of(final IdentityPublicKey publicKey,
                                                 final Object path) {
        return new AutoValue_RemoveSuperPeerAndPathEvent(publicKey, path);
    }
}
