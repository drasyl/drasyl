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
package test.util;

import org.drasyl.handler.membership.cyclon.CyclonNeighbor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static java.util.Objects.requireNonNull;

/**
 * As {@link CyclonNeighbor#equals(Object)} only compares {@link CyclonNeighbor#getAddress()}, we need this
 * special matcher that also compares {@link CyclonNeighbor#getAge()} in addition.
 */
public class EqualNeighborWithSameAge extends TypeSafeMatcher<CyclonNeighbor> {
    private final CyclonNeighbor left;

    private EqualNeighborWithSameAge(final CyclonNeighbor left) {
        this.left = requireNonNull(left);
    }

    @Override
    protected boolean matchesSafely(final CyclonNeighbor right) {
        return left.equals(right) &&
                left.getAge() == right.getAge();
    }

    @Override
    public void describeTo(final Description description) {
        description.appendText("equal neighbor with same age as " + left);
    }

    public static Matcher<CyclonNeighbor> equalNeighborWithSameAge(final CyclonNeighbor left) {
        return new EqualNeighborWithSameAge(left);
    }
}
