/*
 * Copyright (c) 2020-2021.
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
package org.drasyl.test;

import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.drasyl.util.JSONUtil.JACKSON_READER;

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
