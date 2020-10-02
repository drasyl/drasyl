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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Instances of classes that implement this interface are considered <i>signable</i>. A signable
 * object can be converted into a signature and can store that signature itself.
 */
public interface Signable {
    /**
     * <p>
     * Returns a byte representation of the object that can be used for signing the object.
     * <b>Attention</b>: If you omit fields in this method those fields will not be signed. That
     * means that they can be modified by a third party without detection.
     * </p>
     * <p>
     * The default-implementation creates a {@link ByteArrayOutputStream} and calls {@link
     * #writeFieldsTo(OutputStream)} to have content written to it.
     * </p>
     *
     * @return byte-array or <code>null</code>
     */
    @JsonIgnore
    default byte[] getSignableBytes() {
        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            writeFieldsTo(os);
            return os.toByteArray();
        }
        catch (final Exception e) {
            return null; // NOSONAR
        }
    }

    /**
     * <p>
     * Write any content into the passed output-stream to have them included in the signing
     * process.
     * </p>
     * <p>
     * Only use this with the default-implementation of {@link #getSignableBytes()}
     * </p>
     *
     * @param outstream an outputstream to write to
     * @throws IOException
     */
    default void writeFieldsTo(final OutputStream outstream) throws IOException {
        // do nothing ;-)
    }

    /**
     * Returns the signature this signable object was signed with.
     */
    Signature getSignature();

    /**
     * Signs the object with the specified signature. After this method was invoked subsequent calls
     * to {@link #getSignature()} should return a <code>Signature</code> object that is equal to the
     * specified <code>signature</code>.
     */
    void setSignature(Signature signature);
}