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
package org.drasyl.test;

import org.drasyl.identity.Identity;
import org.drasyl.node.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.drasyl.node.JSONUtil.JACKSON_READER;

@SuppressWarnings("unused")
class IdentityProvider {
    Set<Identity> identities = new LinkedHashSet<>();

    public IdentityProvider() {
    }

    public IdentityProvider(final int initialCapacity) throws IOException {
        fill(initialCapacity);
    }

    public IdentityProvider(final Path path) throws IOException {
        for (final File file : path.toFile().listFiles()) {
            if (file.getName().endsWith(".identity.json")) {
                try {
                    identities.add(JACKSON_READER.readValue(file, Identity.class));
                }
                catch (final IOException e) {
                    throw new IOException("Unable to read identity from " + file.toString(), e);
                }
            }
        }
    }

    public void fill(final int count) throws IOException {
        for (int i = 0; i < count; i++) {
            identities.add(IdentityManager.generateIdentity());
        }
    }

    public void fill() throws IOException {
        fill(1);
    }

    @SuppressWarnings("java:S1166")
    public synchronized Identity obtain() throws IOException {
        try {
            final Iterator<Identity> iterator = identities.iterator();
            final Identity identity = iterator.next();
            iterator.remove();
            return identity;
        }
        catch (final NoSuchElementException e) {
            return IdentityManager.generateIdentity();
        }
    }

    public boolean release(final Identity identity) {
        return identities.add(identity);
    }
}
