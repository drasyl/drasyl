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
     */
    public static byte[] fromString(String hexString) {
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
    public static byte[] parseHexBinary(String lexicalXSDHexBinary) {
        final int length = lexicalXSDHexBinary.length();

        // "111" is not a valid hex encoding.
        if (length % 2 != 0) {
            throw new IllegalArgumentException("hexBinary needs to be even-length: " + lexicalXSDHexBinary);
        }

        byte[] out = new byte[length / 2];

        for (int i = 0; i < length; i += 2) {
            int h = hexToBin(lexicalXSDHexBinary.charAt(i));
            int l = hexToBin(lexicalXSDHexBinary.charAt(i + 1));
            if (h == -1 || l == -1) {
                throw new IllegalArgumentException("contains illegal character for hexBinary: " + lexicalXSDHexBinary);
            }

            out[i / 2] = (byte) (h * 16 + l);
        }

        return out;
    }

    private static int hexToBin(char ch) {
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
    public static String toString(byte[] byteArray) {
        return bytesToHex(byteArray);
    }

    /**
     * Converts an array of bytes into a string.
     *
     * @param bytes an array of bytes
     * @return A string hex representation of the byte array
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
