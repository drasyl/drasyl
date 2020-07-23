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
package org.drasyl.messenger;

import org.drasyl.identity.CompressedPublicKey;

/**
 * Is thrown by {@link MessageSink} if it can be excluded that no path to the specified public key
 * exists.
 */
@SuppressWarnings({ "java:S110" })
public class NoPathToPublicKeyException extends MessageSinkException {
    public NoPathToPublicKeyException(CompressedPublicKey identity) {
        super("No Path to " + identity);
    }
}
