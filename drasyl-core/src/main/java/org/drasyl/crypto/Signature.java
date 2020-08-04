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
package org.drasyl.crypto;

import java.util.Arrays;

/**
 * A signature. (Wrapper class for byte[])
 */
public class Signature {
    private final byte[] bytes; // NOSONAR

    private Signature() {
        bytes = new byte[]{};
    }

    /**
     * Signature
     *
     * @param sig
     */
    public Signature(byte[] sig) {
        bytes = sig;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Signature) {
            Signature sig2 = (Signature) obj;
            if (sig2.getBytes().length == bytes.length) {
                for (int i = 0; i < bytes.length; i++) {
                    if (bytes[i] != sig2.getBytes()[i]) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns bytes of signature.
     *
     * @return signature bytes
     */
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return String.format("Signature{%s}", HexUtil.toString(getBytes()));
    }
}