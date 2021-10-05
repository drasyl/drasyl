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
package org.drasyl.handler.serialization;

import java.io.IOException;
import java.util.Arrays;

/**
 * This Serializer (de)serializes {@link Boolean} objects.
 */
public class BooleanSerializer extends BoundedSerializer<Boolean> {
    private static final byte[] TRUE_BYTES = { 1 };
    private static final byte[] FALSE_BYTES = { 0 };

    @Override
    protected byte[] matchedToByArray(final Boolean o) {
        if (Boolean.TRUE.equals(o)) {
            return TRUE_BYTES.clone();
        }
        else {
            return FALSE_BYTES.clone();
        }
    }

    @Override
    protected Boolean matchedFromByteArray(final byte[] bytes,
                                           final Class<Boolean> type) throws IOException {
        if (Arrays.equals(TRUE_BYTES, bytes)) {
            return Boolean.TRUE;
        }
        else if (Arrays.equals(FALSE_BYTES, bytes)) {
            return Boolean.FALSE;
        }
        else {
            throw new IOException("Unexpected bytes value");
        }
    }
}
