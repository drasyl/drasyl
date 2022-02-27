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
package org.drasyl.node.plugin.groups.client;

import com.google.auto.value.AutoValue;
import org.drasyl.util.UnsignedShort;

import static org.drasyl.util.Preconditions.requireInRange;

/**
 * A simple POJO that models a group.
 * <p>
 * This is an immutable object.
 */
@AutoValue
public abstract class Group {
    /**
     * Creates a new group with the given {@code name}.
     *
     * @param name the name of the group
     * @return a group
     */
    public static Group of(final String name) {
        requireInRange(name.length(), UnsignedShort.MIN_VALUE.getValue(), UnsignedShort.MAX_VALUE.getValue());
        return new AutoValue_Group(name);
    }

    public abstract String getName();
}
