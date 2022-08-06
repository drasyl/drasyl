/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.handler.membership.cyclon;

import com.google.auto.value.AutoValue;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Response of a CYCLON shuffle request.
 *
 * @see CyclonShuffleRequest
 */
@AutoValue
public abstract class CyclonShuffleResponse implements CyclonMessage {
    @Override
    public String toString() {
        return "CyclonShuffleResponse{\n" +
                getNeighbors().stream().map(Object::toString).collect(Collectors.joining(",\n\t", "\t", "\n")) +
                '}';
    }

    public static CyclonShuffleResponse of(final Set<CyclonNeighbor> neighbors) {
        return new AutoValue_CyclonShuffleResponse(neighbors);
    }

    public static CyclonShuffleResponse of(final CyclonNeighbor... neighbors) {
        return new AutoValue_CyclonShuffleResponse(Set.of(neighbors));
    }
}
