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
package org.drasyl.util;

import java.time.Duration;

/**
 * Static convenience methods that help a method or constructor check whether it was invoked
 * correctly (that is, whether its <i>preconditions</i> were met).
 * <p>
 * If the precondition is not met, the {@code Preconditions} method throws an unchecked exception of
 * a specified type, which helps the method in which the exception was thrown communicate that its
 * caller has made a mistake.
 */
public final class Preconditions {
    public static final String MUST_BE_NON_NEGATIVE = "must be non-negative";
    public static final String MUST_BE_POSITIVE = "must be positive";
    public static final String MUST_BE_IN_RANGE = "must be in range of [%d, %d]";

    private Preconditions() {
        // util class
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static byte requireNonNegative(final byte obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static byte requireNonNegative(final byte obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static int requireNonNegative(final int obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static int requireNonNegative(final int obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static long requireNonNegative(final long obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static long requireNonNegative(final long obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static short requireNonNegative(final short obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static short requireNonNegative(final short obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static float requireNonNegative(final float obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static float requireNonNegative(final float obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static double requireNonNegative(final double obj) {
        if (obj < 0) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static double requireNonNegative(final double obj, final String message) {
        if (obj < 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     * @throws NullPointerException     if {@code obj} is {@code null}
     */
    public static Duration requireNonNegative(final Duration obj) {
        if (obj.isNegative()) {
            throw new IllegalArgumentException(MUST_BE_NON_NEGATIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is non-negative and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireNonNegative(bar, "bar must be non-negative");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for negativity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     * @throws NullPointerException     if {@code obj} is {@code null}
     */
    public static Duration requireNonNegative(final Duration obj, final String message) {
        if (obj.isNegative()) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static byte requirePositive(final byte obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static byte requirePositive(final byte obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static int requirePositive(final int obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static int requirePositive(final int obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static long requirePositive(final long obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static long requirePositive(final long obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for positivity
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static short requirePositive(final short obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static short requirePositive(final short obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static float requirePositive(final float obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static float requirePositive(final float obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for negativity
     * @return {@code obj} if non-negative
     * @throws IllegalArgumentException if {@code obj} is negative
     */
    public static double requirePositive(final double obj) {
        if (obj <= 0) {
            throw new IllegalArgumentException(MUST_BE_POSITIVE);
        }

        return obj;
    }

    /**
     * Checks that the specified number is positive and throws a customized
     * {@link IllegalArgumentException} if it is not. This method is designed primarily for doing
     * parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requirePositive(bar, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for positivity
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if positive
     * @throws IllegalArgumentException if {@code obj} is not positive
     */
    public static double requirePositive(final double obj, final String message) {
        if (obj <= 0) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static byte requireInRange(final byte obj, final byte min, final byte max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static byte requireInRange(final byte obj,
                                      final byte min,
                                      final byte max,
                                      final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static int requireInRange(final int obj, final int min, final int max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static int requireInRange(final int obj,
                                     final int min,
                                     final int max,
                                     final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static long requireInRange(final long obj, final long min, final long max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static long requireInRange(final long obj,
                                      final long min,
                                      final long max,
                                      final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static short requireInRange(final short obj, final short min, final short max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static short requireInRange(final short obj,
                                       final short min,
                                       final short max,
                                       final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static float requireInRange(final float obj, final float min, final float max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static float requireInRange(final float obj,
                                       final float min,
                                       final float max,
                                       final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}]. This
     * method is designed primarily for doing parameter validation in methods and constructors, as
     * demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max);
     * }
     * </pre></blockquote>
     *
     * @param obj the number to check for range
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static double requireInRange(final double obj, final double min, final double max) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(String.format(MUST_BE_IN_RANGE, min, max));
        }

        return obj;
    }

    /**
     * Checks that the specified number is in the given range [{@code min}, {@code max}] and throws
     * a customized {@link IllegalArgumentException} if it is not. This method is designed primarily
     * for doing parameter validation in methods and constructors, as demonstrated below:
     * <blockquote><pre>
     * public Foo(int bar) {
     *     this.bar = Preconditions.requireInRange(bar, min, max, "bar must be positive");
     * }
     * </pre></blockquote>
     *
     * @param obj     the number to check for range
     * @param message detail message to be used in the event that a {@code IllegalArgumentException}
     *                is thrown
     * @return {@code obj} if in range
     * @throws IllegalArgumentException if {@code obj} is not in range
     */
    public static double requireInRange(final double obj,
                                        final double min,
                                        final double max,
                                        final String message) {
        if (obj < min || obj > max) {
            throw new IllegalArgumentException(message);
        }

        return obj;
    }
}
