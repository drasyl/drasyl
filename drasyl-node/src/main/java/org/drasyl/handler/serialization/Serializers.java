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

/**
 * This class contains some constants with the identifiers of default serializers.
 */
@SuppressWarnings("unused")
public final class Serializers {
    public static final String SERIALIZER_PRIMITIVE_BOOLEAN = "primitive-boolean";
    public static final String SERIALIZER_PRIMITIVE_BYTE = "primitive-byte";
    public static final String SERIALIZER_PRIMITIVE_CHAR = "primitive-char";
    public static final String SERIALIZER_PRIMITIVE_FLOAT = "primitive-float";
    public static final String SERIALIZER_PRIMITIVE_INT = "primitive-int";
    public static final String SERIALIZER_PRIMITIVE_LONG = "primitive-long";
    public static final String SERIALIZER_PRIMITIVE_SHORT = "primitive-short";
    public static final String SERIALIZER_BYTES = "bytes";
    public static final String SERIALIZER_STRING = "string";
    public static final String SERIALIZER_JAVA = "java";
    public static final String SERIALIZER_JACKSON_JSON = "jackson-json";
    public static final String SERIALIZER_PROTO = "proto";

    private Serializers() {
        // util class
    }
}
