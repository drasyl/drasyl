/*
 * Copyright (c) 2020
 *
 * This file is part of drasyl.
 *
 * drasyl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * drasyl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.core.crypto;

import java.security.PublicKey;

/**
 * This interface models a compressed public key that can be converted into a string and vice
 * versa.
 */
public interface CompressedPublicKey {
    /**
     * Converts a {@link String} into a {@link CompressedPublicKey}.
     *
     * @param key compressed public key as String
     * @return {@link CompressedPublicKey}
     */
    public CompressedPublicKey fromString(String key);

    /**
     * Converts a {@link CompressedPublicKey} into a {@link String}.
     *
     * @param key compressed public key as {@link CompressedPublicKey}
     * @return {@link String}
     */
    public String toString(CompressedPublicKey key);

    /**
     * @return Returns this {@link CompressedPublicKey} object as a {@link PublicKey} object.
     */
    public PublicKey getPubKey();
}
