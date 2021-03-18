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
package org.drasyl.pipeline.serialization;

import java.util.Arrays;
import java.util.Objects;

/**
 * A message from or to the application whose content has been serialized to a byte array so that
 * the message can be delivered to remote nodes.
 */
public class SerializedApplicationMessage {
    private final String type;
    private final byte[] content;

    public SerializedApplicationMessage(final String type,
                                        final byte[] content) {
        this.content = content != null ? content.clone() : null;
        this.type = type;
    }

    @Override
    public String toString() {
        return "SerializedApplicationMessage{" +
                "type='" + type + "'," +
                "content=byte[" + content.length + "]" +
                '}';
    }

    public String getType() {
        return type;
    }

    public byte[] getContent() {
        return content != null ? content.clone() : null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SerializedApplicationMessage that = (SerializedApplicationMessage) o;
        return Objects.equals(type, that.type) && Arrays.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
