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

package org.drasyl.core.crypto;

/*
 * Dummy for Testing Keychain
 */
public class DummySignable implements Signable {

    private Signature signature;
    private byte[] bytes;

    public DummySignable() {
        bytes = new byte[] {(byte) 0x3a, (byte) 0x22};
    }

    public DummySignable(byte[] b){
        bytes = b;
    }

    @Override
    public byte[] getSignableBytes() {
        return bytes;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public void setSignature(Signature sig) {
        signature = sig;
    }

}