/*
 * Copyright (c) 2021.
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
package org.drasyl.serialization;

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
            return TRUE_BYTES;
        }
        else {
            return FALSE_BYTES;
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
