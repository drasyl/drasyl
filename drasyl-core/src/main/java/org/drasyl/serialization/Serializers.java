/*
 * Copyright (c) 2020-2021.
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
