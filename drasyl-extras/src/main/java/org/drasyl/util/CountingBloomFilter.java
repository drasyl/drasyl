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
import java.util.function.Function;

import static org.drasyl.util.Preconditions.requirePositive;

/**
 * A special {@link BloomFilter} that allows you to remove elements.
 *
 * @param <E> the type of elements maintained by this set
 */
public class CountingBloomFilter<E> extends BloomFilter<E> {
    private final int countingBits;

    protected CountingBloomFilter(final Parameters parameters,
                                  final Function<E, byte[]> bytesSupplier,
                                  final BitSet bitSet,
                                  final int countingBits) {
        super(parameters, bytesSupplier, bitSet);
        this.countingBits = requirePositive(countingBits);
    }

    protected CountingBloomFilter(final Parameters parameters,
                                  final Function<E, byte[]> bytesSupplier,
                                  final int countingBits) {
        this(parameters, bytesSupplier, new BitSet(parameters.m * countingBits), countingBits);
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
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @param bitSet        {@link BitSet} holding this bloom filter's state. Caller must ensure
     *                      that it has the correct size.
     * @param countingBits  number to bits used for the counter
     * @throws IllegalArgumentException if one parameter is not {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final int m,
                               final int k,
                               final Function<E, byte[]> bytesSupplier,
                               final BitSet bitSet,
                               final int countingBits) {
        this(new Parameters(n, p, m, k), bytesSupplier, bitSet, countingBits);
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
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @param countingBits  number to bits used for the counter
     * @throws IllegalArgumentException if one parameter is not {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final int m,
                               final int k,
                               final Function<E, byte[]> bytesSupplier,
                               final int countingBits) {
        this(new Parameters(n, p, m, k), bytesSupplier, countingBits);
    }

    /**
     * Create a new counting bloom filter with {@code Short.BYTES * 8} bits reserved for the
     * counters. Make sure to leave one parameter {@code 0} as it must be derived from the other
     * parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param m             Number of bits in the filter. If {@code 0}, this value is derived from
     *                      {@code n} and {@code p}
     * @param k             Number of hash functions. If {@code 0}, this value is derived from
     *                      {@code m} and {@code n}
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @param bitSet        {@link BitSet} holding this bloom filter's state. Caller must ensure
     *                      that it has the correct size.
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final int m,
                               final int k,
                               final Function<E, byte[]> bytesSupplier,
                               final BitSet bitSet) {
        this(new Parameters(n, p, m, k), bytesSupplier, bitSet, Short.BYTES * 8);
    }

    /**
     * Create a new counting bloom filter with {@code Short.BYTES * 8} bits reserved for the
     * counters. Make sure to leave one parameter {@code 0} as it must be derived from the other
     * parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param m             Number of bits in the filter. If {@code 0}, this value is derived from
     *                      {@code n} and {@code p}
     * @param k             Number of hash functions. If {@code 0}, this value is derived from
     *                      {@code m} and {@code n}
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final int m,
                               final int k,
                               final Function<E, byte[]> bytesSupplier) {
        this(new Parameters(n, p, m, k), bytesSupplier, Short.BYTES * 8);
    }

    /**
     * Create a new counting bloom filter. Make sure to leave one parameter {@code 0} as it must be
     * derived from the other parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @param countingBits  number to bits used for the counter
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final Function<E, byte[]> bytesSupplier,
                               final int countingBits) {
        this(new Parameters(n, p, 0, 0), bytesSupplier, countingBits);
    }

    /**
     * Create a new counting bloom filter with {@code Short.BYTES * 8} bits reserved for the
     * counters. Make sure to leave one parameter {@code 0} as it must be derived from the other
     * parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final Function<E, byte[]> bytesSupplier) {
        this(new Parameters(n, p, 0, 0), bytesSupplier, Short.BYTES * 8);
    }

    /**
     * Create a new counting bloom filter with {@code Short.BYTES * 8} bits reserved for the
     * counters. Make sure to leave one parameter {@code 0} as it must be derived from the other
     * parameters.
     * <p>
     * Visit <a href="https://hur.st/bloomfilter/">Bloom Filter Calculator</a> to get more
     * information about the implications/calculation of these parameters.
     *
     * @param n             Number of items in the filter
     * @param p             Probability of false positives, fraction between 0 and 1
     * @param bytesSupplier a {@link Function} that convert objects of type {@code E} to {@code
     *                      byte[]} arrays
     * @param bitSet        {@link BitSet} holding this bloom filter's state. Caller must ensure
     *                      that it has the correct size.
     * @throws IllegalArgumentException if {@code n} or {@code p} is {@code 0} or {@code p} is not
     *                                  between 0 and 1
     * @throws NullPointerException     if {@code bytesSupplier} is {@code null}
     */
    public CountingBloomFilter(final int n,
                               final double p,
                               final Function<E, byte[]> bytesSupplier,
                               final BitSet bitSet) {
        this(new Parameters(n, p, 0, 0), bytesSupplier, bitSet, Short.BYTES * 8);
    }

    @Override
    public String toString() {
        return "CountingBloomFilter{" +
                "n=" + parameters.n +
                ", p=" + parameters.p +
                ", m=" + parameters.m +
                ", k=" + parameters.k +
                ", countingBits=" + countingBits +
                '}';
    }

    @Override
    public boolean remove(final Object o) {
        try {
            boolean modified = false;
            final int[] hashes = hashes((E) o);
            for (final int hash : hashes) {
                if (unsetBit(hash)) {
                    modified = true;
                }
            }
            return modified;
        }
        catch (final ClassCastException e) {
            return false;
        }
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
        boolean modified = false;
        for (final Object e : c) {
            if (remove(e)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public void merge(BloomFilter<E> other) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean getBit(final int index) {
        final BitSet countingBitSet = this.bitSet.get(index, index + countingBits);
        return !countingBitSet.isEmpty();
    }

    @Override
    protected boolean setBit(final int index) {
        for (int i = 0; i < countingBits; i++) {
            boolean bit = bitSet.get(index + i);
            if (bit) {
                if (i == countingBits - 1) {
                    throw new IllegalStateException("Counter overflow detected. Bloom filter corrupted.");
                }
                bitSet.set(index + i, false);
            }
            else {
                bitSet.set(index + i, true);
                break;
            }
        }
        return true;
    }

    protected boolean unsetBit(final int index) {
        for (int i = 0; i < countingBits; i++) {
            boolean bit = bitSet.get(index + i);
            if (bit) {
                bitSet.set(index + i, false);
                break;
            }
            else {
                if (i == countingBits - 1) {
                    throw new IllegalStateException("counter underflow detected. Bloom filter corrupted.");
                }
                bitSet.set(index + i, true);
            }
        }
        return true;
    }
}
