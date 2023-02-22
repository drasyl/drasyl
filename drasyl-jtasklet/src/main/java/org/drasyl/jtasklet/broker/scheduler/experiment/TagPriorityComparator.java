/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.jtasklet.broker.scheduler.experiment;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.jtasklet.broker.ResourceProvider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class TagPriorityComparator implements Comparator<Map.Entry<DrasylAddress, ResourceProvider>> {
    private final List<String> tags;

    public TagPriorityComparator(final List<String> tags) {
        this.tags = tags;
    }

    @Override
    public int compare(final Map.Entry<DrasylAddress, ResourceProvider> o1,
                       final Map.Entry<DrasylAddress, ResourceProvider> o2) {
        for (final String tag : tags) {
            if (o1.getValue().tags().indexOf(tag) > o2.getValue().tags().indexOf(tag)) {
                return 1;
            }
            else {
                return -1;
            }
        }

        return 0;
    }
}
