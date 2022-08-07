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
package org.drasyl.handler.membership.cyclon;

import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.Preconditions;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * A CYCLON neighbor.
 *
 * @see CyclonView
 */
public final class CyclonNeighbor implements Comparable<CyclonNeighbor> {
    private final DrasylAddress address;
    private int age;

    private CyclonNeighbor(final DrasylAddress address, final int age) {
        this.address = requireNonNull(address);
        this.age = Preconditions.requireNonNegative(age);
    }

    public DrasylAddress getAddress() {
        return address;
    }

    public int getAge() {
        return age;
    }

    public void increaseAgeByOne() {
        age += 1;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CyclonNeighbor neighbor = (CyclonNeighbor) o;
        return address.equals(neighbor.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return "CyclonNeighbor{" +
                "address=" + address +
                ", age=" + age +
                '}';
    }

    @Override
    public int compareTo(final CyclonNeighbor o) {
        return Integer.compare(age, o.age);
    }

    public static CyclonNeighbor of(final DrasylAddress address,
                                    final int age) {
        return new CyclonNeighbor(address, age);
    }

    public static CyclonNeighbor of(final DrasylAddress address) {
        return of(address, 0);
    }
}
