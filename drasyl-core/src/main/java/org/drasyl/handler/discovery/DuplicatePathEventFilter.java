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
package org.drasyl.handler.discovery;

import org.drasyl.identity.DrasylAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This class can be used to filter equal events for a given peer (identifies by {@link
 * PathEvent#getAddress()}).
 */
public class DuplicatePathEventFilter {
    private final Map<DrasylAddress, PathEvent> pathEvents;

    DuplicatePathEventFilter(final Map<DrasylAddress, PathEvent> pathEvents) {
        this.pathEvents = requireNonNull(pathEvents);
    }

    public DuplicatePathEventFilter() {
        this(new HashMap<>());
    }

    /**
     * @return {@code true} if the last (if any) {@link PathEvent} is not equal to {@link event}.
     * Otherwise {@code false}.
     */
    public boolean add(final PathEvent event) {
        return !Objects.equals(pathEvents.put(event.getAddress(), event), event);
    }
}
