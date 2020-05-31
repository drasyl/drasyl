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

import org.bouncycastle.util.encoders.Hex;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
//@Fork(value = 1)
//@Warmup(iterations = 3)
//@Measurement(iterations = 3)
public class HexUtilBenchmark {
    private final byte[] byteArray;

    public HexUtilBenchmark() {
        byteArray = new byte[]{ 0x4f, 0x00, 0x10, 0x0d };
    }

    @Benchmark
    public void ownByteToString() {
        HexUtil.toString(byteArray);
    }

    @Benchmark
    public void bouncycastleByteToString() {
        Hex.toHexString(byteArray);
    }
}
