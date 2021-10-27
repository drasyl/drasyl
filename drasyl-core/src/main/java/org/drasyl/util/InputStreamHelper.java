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
package org.drasyl.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class that provides utils for {@link InputStream}s.
 */
public class InputStreamHelper {
    private InputStreamHelper() {
        // util class
    }

    /**
     * Reads all remaining bytes from the input stream. This method blocks until all remaining bytes
     * have been read and end of stream is detected, or an exception is thrown. This method does not
     * close the input stream.
     *
     * <p> When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     * <p> Note that this method is intended for simple cases where it is
     * convenient to read all bytes into a byte array. It is not intended for reading input streams
     * with large amounts of data.
     *
     * <p> The behavior for the case where the input stream is <i>asynchronously
     * closed</i>, or the thread interrupted during the read, is highly input stream specific, and
     * therefore not specified.
     *
     * <p> If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently the input stream may not be at
     * end of stream and may be in an inconsistent state. It is strongly recommended that the stream
     * be promptly closed if an I/O error occurs.
     *
     * <p> Source: <a href="https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/io/InputStream.java">
     * https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/io/InputStream.java</a>
     *
     * @return a byte array containing the bytes read from this input stream
     * @throws IOException      if an I/O error occurs
     * @throws OutOfMemoryError if an array of the required size cannot be allocated.
     * @implSpec This method invokes {@link #readNBytes(InputStream, int)} with a length of {@link
     * Integer#MAX_VALUE}.
     * @since 9
     */
    public static byte[] readAllBytes(final InputStream is) throws IOException {
        return readNBytes(is, Integer.MAX_VALUE);
    }

    /**
     * Reads up to a specified number of bytes from the input stream. This method blocks until the
     * requested number of bytes has been read, end of stream is detected, or an exception is
     * thrown. This method does not close the input stream.
     *
     * <p> The length of the returned array equals the number of bytes read
     * from the stream. If {@code len} is zero, then no bytes are read and an empty byte array is
     * returned. Otherwise, up to {@code len} bytes are read from the stream. Fewer than {@code len}
     * bytes may be read if end of stream is encountered.
     *
     * <p> When this stream reaches end of stream, further invocations of this
     * method will return an empty byte array.
     *
     * <p> Note that this method is intended for simple cases where it is
     * convenient to read the specified number of bytes into a byte array. The total amount of
     * memory allocated by this method is proportional to the number of bytes read from the stream
     * which is bounded by {@code len}. Therefore, the method may be safely called with very large
     * values of {@code len} provided sufficient memory is available.
     *
     * <p> The behavior for the case where the input stream is <i>asynchronously
     * closed</i>, or the thread interrupted during the read, is highly input stream specific, and
     * therefore not specified.
     *
     * <p> If an I/O error occurs reading from the input stream, then it may do
     * so after some, but not all, bytes have been read. Consequently the input stream may not be at
     * end of stream and may be in an inconsistent state. It is strongly recommended that the stream
     * be promptly closed if an I/O error occurs.
     *
     * <p> Source: <a href="https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/io/InputStream.java">
     * https://github.com/AdoptOpenJDK/openjdk-jdk11/blob/master/src/java.base/share/classes/java/io/InputStream.java</a>
     *
     * @param len the maximum number of bytes to read
     * @return a byte array containing the bytes read from this input stream
     * @throws IllegalArgumentException if {@code length} is negative
     * @throws IOException              if an I/O error occurs
     * @throws OutOfMemoryError         if an array of the required size cannot be allocated.
     *                                  <p>
     *                                  Source: {@link java.io.InputStream#readNBytes(int)}
     * @implNote The number of bytes allocated to read data from this stream and return the result
     * is bounded by {@code 2*(long)len}, inclusive.
     */
    public static byte[] readNBytes(final InputStream is, final int len) throws IOException {
        if (len < 0) {
            throw new IllegalArgumentException("len < 0");
        }

        List<byte[]> bufs = null;
        byte[] result = null;
        int total = 0;
        int remaining = len;
        int n;
        do {
            byte[] buf = new byte[Math.min(remaining, 8192)];
            int nread = 0;

            // read to EOF which may read more or less than buffer size
            while ((n = is.read(buf, nread,
                    Math.min(buf.length - nread, remaining))) > 0) {
                nread += n;
                remaining -= n;
            }

            if (nread > 0) {
                if ((Integer.MAX_VALUE - 8) - total < nread) {
                    throw new OutOfMemoryError("Required array size too large");
                }
                if (nread < buf.length) {
                    buf = Arrays.copyOfRange(buf, 0, nread);
                }
                total += nread;
                if (result == null) {
                    result = buf;
                }
                else {
                    if (bufs == null) {
                        bufs = new ArrayList<>();
                        bufs.add(result);
                    }
                    bufs.add(buf);
                }
            }
            // if the last call to read returned -1 or the number of bytes
            // requested have been read then break
        } while (n >= 0 && remaining > 0);

        if (bufs == null) {
            if (result == null) {
                return new byte[0];
            }
            return result.length == total ?
                    result : Arrays.copyOf(result, total);
        }

        result = new byte[total];
        int offset = 0;
        remaining = total;
        for (final byte[] b : bufs) {
            final int count = Math.min(b.length, remaining);
            System.arraycopy(b, 0, result, offset, count);
            offset += count;
            remaining -= count;
        }

        return result;
    }
}
