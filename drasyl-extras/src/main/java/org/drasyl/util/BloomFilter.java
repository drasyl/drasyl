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
package org.drasyl.util;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * A bloom filter is a probabilistic data structure that can quickly and efficiently check whether
 * an element is included in a set.
 * <p>
 * This implementation uses {@link Murmur3.murmur3_x86_32} as hash source is backed by a
 * {@link BitSet}.
 * <p>
 * Although this class implements {@link Set}, not all set operations are actually supported (e.g.
 * removal or retrieval of elements).
 *
 * @param <E> the type of elements maintained by this set
 */
public class BloomFilter<E> implements Set<E> {
    private static final int MURMUR_SEED1 = 1258387308;
    private static final int MURMUR_SEED2 = 2021488126;
    protected final Parameters parameters;
    protected final BitSet bitSet;
    protected final Function<E, byte[]> bytesSupplier;

    protected BloomFilter(final Parameters parameters,
                          final Function<E, byte[]> bytesSupplier,
                          final BitSet bitSet) {
        this.parameters = requireNonNull(parameters);
        this.bytesSupplier = requireNonNull(bytesSupplier);
        this.bitSet = requireNonNull(bitSet);
    }

    protected BloomFilter(final Parameters parameters,
                          final Function<E, byte[]> bytesSupplier) {
        this(parameters, bytesSupplier, new BitSet(parameters.m));
    }

    /**
     * Create a new bloom filter. Make sure to leave one parameter {@code 0} as it must be derived
     * from the other parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     * <p>
     * If {@code m} is not a multiple of MurmurHash3 x86 32-bit hash, this bloom filter is subject
     * of the "modulo bias" effect.
     *
     * @param n             Number of items in the filter. If {@code 0}, this value is derived from
     *                      {@code p}, {@code m}, and {@code k}
     * @param p             Probability of false positives, fraction between 0 and 1. If {@code 0},
     *                      this value is derived from {@code n}, {@code m}, and {@code k}
     * @param m             Number of bits in the filter. If {@code 0}, this value is derived from
     *                      {@code n} and {@code p}
     * @param k             Number of hash functions. If {@code 0}, this value is derived from
     *                      {@code m} and {@code n}
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to
     *                      {@code byte[]} arrays
     * @param bitSet        {@link BitSet} holding this bloom filter's state. Caller must ensure
     *                      that it has the correct size.
     * @throws IllegalArgumentException if one parameter is not {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public BloomFilter(final int n,
                       final double p,
                       final int m,
                       final int k,
                       final Function<E, byte[]> bytesSupplier,
                       final BitSet bitSet) {
        this(new Parameters(n, p, m, k), bytesSupplier, bitSet);
    }

    /**
     * Create a new bloom filter. Make sure to leave one parameter {@code 0} as it must be derived
     * from the other parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     * <p>
     * If {@code m} is not a multiple of MurmurHash3 x86 32-bit hash, this bloom filter is subject
     * of the "modulo bias" effect.
     *
     * @param n             Number of items in the filter. If {@code 0}, this value is derived from
     *                      {@code p}, {@code m}, and {@code k}
     * @param p             Probability of false positives, fraction between 0 and 1. If {@code 0},
     *                      this value is derived from {@code n}, {@code m}, and {@code k}
     * @param m             Number of bits in the filter. If {@code 0}, this value is derived from
     *                      {@code n} and {@code p}
     * @param k             Number of hash functions. If {@code 0}, this value is derived from
     *                      {@code m} and {@code n}
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to
     *                      {@code byte[]} arrays
     * @throws IllegalArgumentException if one parameter is not {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public BloomFilter(final int n,
                       final double p,
                       final int m,
                       final int k,
                       final Function<E, byte[]> bytesSupplier) {
        this(new Parameters(n, p, m, k), bytesSupplier);
    }

    /**
     * Create a new bloom filter. Make sure to leave one parameter {@code 0} as it must be derived
     * from the other parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to
     *                      {@code byte[]} arrays
     * @param bitSet        {@link BitSet} holding this bloom filter's state. Caller must ensure
     *                      that it has the correct size.
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public BloomFilter(final Integer n,
                       final Double p,
                       final Function<E, byte[]> bytesSupplier,
                       final BitSet bitSet) {
        this(n, p, 0, 0, bytesSupplier, bitSet);
    }

    /**
     * Create a new bloom filter. Make sure to leave one parameter {@code 0} as it must be derived
     * from the other parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to
     *                      {@code byte[]} arrays
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public BloomFilter(final Integer n,
                       final Double p,
                       final Function<E, byte[]> bytesSupplier) {
        this(n, p, 0, 0, bytesSupplier);
    }

    @Override
    public String toString() {
        return "BloomFilter{" +
                "n=" + parameters.n +
                ", p=" + parameters.p +
                ", m=" + parameters.m +
                ", k=" + parameters.k +
                '}';
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return bitSet.isEmpty();
    }

    /**
     * Checks if {@code o} is contained in the filter. False positive messages may occur.
     *
     * @param o element to check
     * @return if {@code element}
     */
    public boolean contains(final Object o) {
        try {
            final int[] hashes = hashes((E) o);
            for (final int hash : hashes) {
                if (!getBit(hash)) {
                    return false;
                }
            }
            return true;
        }
        catch (final ClassCastException e) {
            return false;
        }
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public <T> T[] toArray(final T[] a) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds {@code element} to the filter.
     *
     * @param element element to add
     * @return {@code true} if adding this element changed the bloom filter. Can be used as an
     * indicate if {@code element} was previously not contained in the filter. But false negatives
     * can occur.
     */
    public boolean add(final E element) {
        boolean modified = false;
        final int[] hashes = hashes(element);
        for (final int hash : hashes) {
            if (setBit(hash)) {
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean remove(final Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(final Collection<?> c) {
        for (final Object e : c) {
            if (!contains(e)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(final Collection<? extends E> c) {
        boolean modified = false;
        for (final E e : c) {
            if (add(e)) {
                modified = true;
            }
        }
        return modified;
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean retainAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported. Throws {@link UnsupportedOperationException}.
     *
     * @return throws {@link UnsupportedOperationException
     * @throws UnsupportedOperationException
     */
    @Override
    public boolean removeAll(final Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        bitSet.clear();
    }

    /**
     * Returns the number of items in the filter.
     *
     * @return number of items in the filter
     */
    public int n() {
        return parameters.n;
    }

    /**
     * Returns the probability of false positives, fraction between 0 and 1.
     *
     * @return probability of false positives, fraction between 0 and 1
     */
    public double p() {
        return parameters.p;
    }

    /**
     * Returns the number of bits in the filter.
     *
     * @return number of bits in the filter
     */
    public int m() {
        return parameters.m;
    }

    /**
     * Returns the number of hash functions.
     *
     * @return number of hash functions
     */
    public int k() {
        return parameters.k;
    }

    /**
     * Returns the {@link BitSet} holding the internal bloom filter state.
     *
     * @return {@link BitSet} holding the internal bloom filter state
     */
    public BitSet bitset() {
        return bitSet;
    }

    /**
     * Merges this filter with {@code other}. While this filter will be contain the merged result,
     * {@code other} will be unchanged.
     *
     * @param other bloom filter to merge
     * @throws IllegalArgumentException if other bloom filter is incompatible (n, p, m, and k must
     *                                  be identical on both filters)
     */
    public void merge(final BloomFilter<E> other) {
        if (!parameters.equals(other.parameters)) {
            throw new IllegalArgumentException("Bloom filters are incompatible");
        }

        bitSet.or(other.bitSet);
    }

    protected boolean getBit(final int index) {
        return bitSet.get(index);
    }

    protected boolean setBit(final int index) {
        final boolean wasUnset = !getBit(index);
        if (wasUnset) {
            bitSet.set(index, true);
        }
        return wasUnset;
    }

    /**
     * Calculates {@code k} hashes for {@code element}.
     *
     * @param element element for which hashes are to be calculated.
     * @return calculated hashes for {@code element}
     */
    protected int[] hashes(final E element) {
        // According to "Less hashing, same performance: Building a better Bloom filter" by Kirsch
        // and Mitzenmacher (https://doi.org/10.1002/rsa.20208), only two hashes are necessary to
        // effectively implement a bloom filter without any loss in the asymptoptic false positive
        // propability.
        final byte[] bytes = bytesSupplier.apply(element);
        final int hash1 = Murmur3.murmur3_x86_32(bytes, MURMUR_SEED1);
        final int hash2 = Murmur3.murmur3_x86_32(bytes, MURMUR_SEED2);
        final int[] hashes = new int[parameters.k];
        for (int i = 0; i < parameters.k; i++) {
            hashes[i] = Math.abs(hash1 + (i * hash2)) % parameters.m;
        }
        return hashes;
    }

    protected static class Parameters {
        protected final int n;
        protected final double p;
        protected final int m;
        protected final int k;

        @SuppressWarnings("java:S3776")
        public Parameters(int n,
                          double p,
                          int m,
                          int k) {
            if (n != 0 && p != 0 && m != 0 && k != 0) {
                throw new IllegalArgumentException("leave an attribute 0 so it can be dervied");
            }
            else if (n == 0) {
                if (p == 0.0) {
                    throw new IllegalArgumentException("need p to derive n");
                }
                if (m == 0) {
                    throw new IllegalArgumentException("need m to derive n");
                }
                if (k == 0) {
                    throw new IllegalArgumentException("need k to derive n");
                }

                n = (int) Math.ceil(m / (-k / Math.log(1 - Math.exp(Math.log(p) / k))));
            }
            else if (p == 0.0) {
                if (m == 0) {
                    throw new IllegalArgumentException("need m to derive p");
                }
                if (k == 0) {
                    throw new IllegalArgumentException("need k to derive p");
                }

                p = Math.pow(1 - Math.exp(-k / (m / n)), k);
            }
            else if (p <= 0.0 || p >= 1.0) {
                throw new IllegalArgumentException("p must be a fraction between 0 and 1");
            }
            else if (m == 0) {
                m = (int) Math.ceil((n * Math.log(p)) / Math.log(1 / Math.pow(2, Math.log(2))));
            }
            if (k == 0) {
                k = (int) Math.round((m / n) * Math.log(2));
            }
            this.n = n;
            this.p = p;
            this.m = m;
            this.k = k;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Parameters that = (Parameters) o;
            return n == that.n && Double.compare(that.p, p) == 0 && m == that.m && k == that.k;
        }

        @Override
        public int hashCode() {
            return Objects.hash(n, p, m, k);
        }
    }
}
