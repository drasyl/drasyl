package org.drasyl.core.client;

import java.io.Serializable;

/**
 * A tuple of two elements.
 */
public class Pair<A, B> implements Serializable {
    private final A first;
    private final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair [first=" + first() + ", second=" + second() + "]";
    }

    public A first() {
        return first;
    }

    public B second() {
        return second;
    }
}
