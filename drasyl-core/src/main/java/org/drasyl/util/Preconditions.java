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
     * Checks that the specified number is non-negative and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is non-negative and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is non-negative and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is non-negative and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is positive and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is positive and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is positive and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
     * Checks that the specified number is positive and throws a customized {@link
     * IllegalArgumentException} if it is not. This method is designed primarily for doing parameter
     * validation in methods and constructors, as demonstrated below:
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
}
