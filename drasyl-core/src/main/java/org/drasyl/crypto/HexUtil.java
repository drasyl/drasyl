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
package org.drasyl.crypto;

/**
 * Util class that provides hexadecimal functions for drasyl.
 */
@SuppressWarnings("java:S109")
public final class HexUtil {
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    private HexUtil() {
        // util class
    }

    /**
     * Converts a Hexadecimal String into the corresponding byte[]
     *
     * @param hexString e.g. "AB34"
     * @return byte array {AB,34}
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space
     *                                  defined in XML Schema Part 2: Datatypes for xsd:hexBinary.
     */
    public static byte[] fromString(final String hexString) {
        return parseHexBinary(hexString);
    }

    /**
     * Converts the string argument into an array of bytes.
     *
     * @param lexicalXSDHexBinary A string containing lexical representation of xsd:hexBinary.
     * @return An array of bytes represented by the string argument.
     * @throws IllegalArgumentException if string parameter does not conform to lexical value space
     *                                  defined in XML Schema Part 2: Datatypes for xsd:hexBinary.
     */
    public static byte[] parseHexBinary(final String lexicalXSDHexBinary) {
        final int length = lexicalXSDHexBinary.length();

        // "111" is not a valid hex encoding.
        if (length % 2 != 0) {
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + lexicalXSDHexBinary);
        }

        final byte[] out = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            final int h = hexToBin(lexicalXSDHexBinary.charAt(i));
            final int l = hexToBin(lexicalXSDHexBinary.charAt(i + 1));
            if (h == -1 || l == -1) {
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + lexicalXSDHexBinary);
            }

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    @SuppressWarnings("java:S1142")
    private static int hexToBin(final char ch) {
        if ('0' <= ch && ch <= '9') {
            return ch - '0';
        }
        if ('A' <= ch && ch <= 'F') {
            return ch - 'A' + 10;
        }
        if ('a' <= ch && ch <= 'f') {
            return ch - 'a' + 10;
        }
        return -1;
    }

    /**
     * Converts a byte[] into a string representation
     *
     * @param byteArray e.g {AB,34}
     * @return string "AB34"
     */
    public static String toString(final byte[] byteArray) {
        return bytesToHex(byteArray);
    }

    /**
     * Converts an array of bytes into a string.
     *
     * @param bytes an array of bytes
     * @return A string hex representation of the byte array
     */
    public static String bytesToHex(final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        final String s = new String(hexChars);
        return s;
    }
}
