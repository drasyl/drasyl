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
/*
 * Copyright 2013 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
/*
 * Copyright (c) 2004-2011 QOS.ch All rights reserved.
 * <p>
 * Permission is hereby granted, free  of charge, to any person obtaining a  copy  of this  software
 * and  associated  documentation files  (the "Software"), to  deal in  the Software without
 * restriction, including without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to permit persons to whom the
 * Software  is furnished to do so, subject to the following conditions:
 * <p>
 * The  above  copyright  notice  and  this permission  notice  shall  be included in all copies or
 * substantial portions of the Software.
 * <p>
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND, EXPRESS OR  IMPLIED,
 * INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF MERCHANTABILITY,    FITNESS    FOR    A
 * PARTICULAR    PURPOSE    AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package org.drasyl.util.logging;

import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

// contributors: lizongbo: proposed special treatment of array parameter values
// Joern Huxhorn: pointed out double[] omission, suggested deep array copy

/**
 * Formats messages according to very simple substitution rules. Substitutions can be made 1, 2 or
 * more arguments.
 * <p/>
 * <p/>
 * For example,
 * <p/>
 * <pre>
 * MessageFormatter.format(&quot;Hi {}.&quot;, &quot;there&quot;)
 * </pre>
 * <p/>
 * will return the string "Hi there.".
 * <p/>
 * The {} pair is called the <em>formatting anchor</em>. It serves to designate the location where
 * arguments need to be substituted within the message pattern.
 * <p/>
 * In case your message contains the '{' or the '}' character, you do not have to do anything
 * special unless the '}' character immediately follows '{'. For example,
 * <p/>
 * <pre>
 * MessageFormatter.format(&quot;Set {1,2,3} is not equal to {}.&quot;, &quot;1,2&quot;);
 * </pre>
 * <p/>
 * will return the string "Set {1,2,3} is not equal to 1,2.".
 * <p/>
 * <p/>
 * If for whatever reason you need to place the string "{}" in the message without its
 * <em>formatting anchor</em> meaning, then you need to escape the '{' character with '\', that is
 * the backslash character. Only the '{' character should be escaped. There is no need to escape the
 * '}' character. For example,
 * <p/>
 * <pre>
 * MessageFormatter.format(&quot;Set \\{} is not equal to {}.&quot;, &quot;1,2&quot;);
 * </pre>
 * <p/>
 * will return the string "Set {} is not equal to 1,2.".
 * <p/>
 * <p/>
 * The escaping behavior just described can be overridden by escaping the escape character '\'.
 * Calling
 * <p/>
 * <pre>
 * MessageFormatter.format(&quot;File name is C:\\\\{}.&quot;, &quot;file.zip&quot;);
 * </pre>
 * <p/>
 * will return the string "File name is C:\file.zip".
 * <p/>
 * <p/>
 * The formatting conventions are different than those of {@link MessageFormat} which ships with the
 * Java platform. This is justified by the fact that SLF4J's implementation is 10 times faster than
 * that of {@link MessageFormat}. This local performance difference is both measurable and
 * significant in the larger context of the complete logging processing chain.
 * <p/>
 * <p/>
 * See also {@link #format(String, Object)}, {@link #format(String, Object, Object)} and
 * {@link #arrayFormat(String, Object[])} methods for more details.
 */
final class MessageFormatter {
    private static final String DELIM_STR = "{}";
    private static final char ESCAPE_CHAR = '\\';

    private MessageFormatter() {
        // util class
    }

    /**
     * Performs single argument substitution for the 'messagePattern' passed as parameter.
     * <p/>
     * For example,
     * <p/>
     * <pre>
     * MessageFormatter.format(&quot;Hi {}.&quot;, &quot;there&quot;);
     * </pre>
     * <p/>
     * will return the string "Hi there.".
     * <p/>
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param arg            The argument to be substituted in place of the formatting anchor
     * @return The formatted message
     */
    static FormattingTuple format(final String messagePattern,
                                  final Object arg) {
        return arrayFormat(messagePattern, new Object[]{ arg });
    }

    /**
     * Performs a two argument substitution for the 'messagePattern' passed as parameter.
     * <p/>
     * For example,
     * <p/>
     * <pre>
     * MessageFormatter.format(&quot;Hi {}. My name is {}.&quot;, &quot;Alice&quot;, &quot;Bob&quot;);
     * </pre>
     * <p/>
     * will return the string "Hi Alice. My name is Bob.".
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param argA           The argument to be substituted in place of the first formatting anchor
     * @param argB           The argument to be substituted in place of the second formatting
     *                       anchor
     * @return The formatted message
     */
    static FormattingTuple format(final String messagePattern,
                                  final Object argA, final Object argB) {
        return arrayFormat(messagePattern, new Object[]{ argA, argB });
    }

    /**
     * Performs a two argument substitution for the 'messagePattern' passed as parameter.
     * <p/>
     * For example,
     * <p/>
     * <pre>
     * MessageFormatter.format(&quot;Hi {}. My name is {}.&quot;, &quot;Alice&quot;, &quot;Bob&quot;);
     * </pre>
     * <p/>
     * will return the string "Hi Alice. My name is Bob.".
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param args           The arguments to be substituted in place of the formatting anchor
     * @return The formatted message
     */
    static FormattingTuple format(final String messagePattern,
                                  final Object... args) {
        return arrayFormat(messagePattern, args);
    }

    /**
     * Same principle as the {@link #format(String, Object)} and
     * {@link #format(String, Object, Object)} methods except that any number of arguments can be
     * passed in an array.
     *
     * @param messagePattern The message pattern which will be parsed and formatted
     * @param argArray       An array of arguments to be substituted in place of formatting anchors
     * @return The formatted message
     */
    @SuppressWarnings({ "java:S109", "java:S117", "java:S1142", "java:S1541", "java:S3776" })
    static FormattingTuple arrayFormat(final String messagePattern,
                                       final Object[] argArray) {
        if (argArray == null || argArray.length == 0) {
            return new FormattingTuple(messagePattern, null);
        }

        final int lastArrIdx = argArray.length - 1;
        final Object lastEntry = argArray[lastArrIdx];
        final Throwable throwable = lastEntry instanceof Throwable ? (Throwable) lastEntry : null;

        if (messagePattern == null) {
            return new FormattingTuple(null, throwable);
        }

        int j = messagePattern.indexOf(DELIM_STR);
        if (j == -1) {
            // this is a simple string
            return new FormattingTuple(messagePattern, throwable);
        }

        final StringBuilder sbuf = new StringBuilder(messagePattern.length() + 50);
        int i = 0;
        int l = 0;
        do {
            boolean notEscaped = j == 0 || messagePattern.charAt(j - 1) != ESCAPE_CHAR;
            if (notEscaped) {
                // normal case
                sbuf.append(messagePattern, i, j);
            }
            else {
                sbuf.append(messagePattern, i, j - 1);
                // check that escape char is not is escaped: "abc x:\\{}"
                notEscaped = j >= 2 && messagePattern.charAt(j - 2) == ESCAPE_CHAR;
            }

            i = j + 2;
            if (notEscaped) {
                deeplyAppendParameter(sbuf, argArray[l], null);
                l++;
                if (l > lastArrIdx) {
                    break;
                }
            }
            else {
                sbuf.append(DELIM_STR);
            }
            j = messagePattern.indexOf(DELIM_STR, i);
        } while (j != -1);

        // append the characters following the last {} pair.
        sbuf.append(messagePattern, i, messagePattern.length());
        return new FormattingTuple(sbuf.toString(), l <= lastArrIdx ? throwable : null);
    }

    // special treatment of array values was suggested by 'lizongbo'
    @SuppressWarnings({ "java:S1541", "java:S3776" })
    private static void deeplyAppendParameter(final StringBuilder sbuf, final Object o,
                                              final Set<Object[]> seenSet) {
        if (o == null) {
            sbuf.append("null");
            return;
        }
        final Class<?> objClass = o.getClass();
        if (!objClass.isArray()) {
            if (Number.class.isAssignableFrom(objClass)) {
                // Prevent String instantiation for some number types
                if (objClass == Long.class) {
                    sbuf.append(((Long) o).longValue());
                }
                else if (objClass == Integer.class || objClass == Short.class || objClass == Byte.class) {
                    sbuf.append(((Number) o).intValue());
                }
                else if (objClass == Double.class) {
                    sbuf.append(((Double) o).doubleValue());
                }
                else if (objClass == Float.class) {
                    sbuf.append(((Float) o).floatValue());
                }
                else {
                    safeObjectAppend(sbuf, o);
                }
            }
            else {
                safeObjectAppend(sbuf, o);
            }
        }
        else {
            // check for primitive array types because they
            // unfortunately cannot be cast to Object[]
            sbuf.append('[');
            if (objClass == boolean[].class) {
                booleanArrayAppend(sbuf, (boolean[]) o);
            }
            else if (objClass == byte[].class) {
                byteArrayAppend(sbuf, (byte[]) o);
            }
            else if (objClass == char[].class) {
                charArrayAppend(sbuf, (char[]) o);
            }
            else if (objClass == short[].class) {
                shortArrayAppend(sbuf, (short[]) o);
            }
            else if (objClass == int[].class) {
                intArrayAppend(sbuf, (int[]) o);
            }
            else if (objClass == long[].class) {
                longArrayAppend(sbuf, (long[]) o);
            }
            else if (objClass == float[].class) {
                floatArrayAppend(sbuf, (float[]) o);
            }
            else if (objClass == double[].class) {
                doubleArrayAppend(sbuf, (double[]) o);
            }
            else {
                objectArrayAppend(sbuf, (Object[]) o, seenSet);
            }
            sbuf.append(']');
        }
    }

    @SuppressWarnings({ "java:S106", "java:S1166", "java:S1181" })
    private static void safeObjectAppend(final StringBuilder sbuf, final Object o) {
        try {
            final String oAsString = o.toString();
            sbuf.append(oAsString);
        }
        catch (final Throwable e) {
            System.err
                    .println("SLF4J: Failed toString() invocation on an object of type [" +
                            o.getClass().getName() + ']');
            e.printStackTrace();
            sbuf.append("[FAILED toString()]");
        }
    }

    private static void objectArrayAppend(final StringBuilder sbuf,
                                          final Object[] a,
                                          Set<Object[]> seenSet) {
        if (a.length == 0) {
            return;
        }
        if (seenSet == null) {
            seenSet = new HashSet<>(a.length);
        }
        if (seenSet.add(a)) {
            deeplyAppendParameter(sbuf, a[0], seenSet);
            for (int i = 1; i < a.length; i++) {
                sbuf.append(", ");
                deeplyAppendParameter(sbuf, a[i], seenSet);
            }
            // allow repeats in siblings
            seenSet.remove(a);
        }
        else {
            sbuf.append("...");
        }
    }

    private static void booleanArrayAppend(final StringBuilder sbuf, final boolean[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void byteArrayAppend(final StringBuilder sbuf, final byte[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void charArrayAppend(final StringBuilder sbuf, final char[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void shortArrayAppend(final StringBuilder sbuf, final short[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void intArrayAppend(final StringBuilder sbuf, final int[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void longArrayAppend(final StringBuilder sbuf, final long[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void floatArrayAppend(final StringBuilder sbuf, final float[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }

    private static void doubleArrayAppend(final StringBuilder sbuf, final double[] a) {
        if (a.length == 0) {
            return;
        }
        sbuf.append(a[0]);
        for (int i = 1; i < a.length; i++) {
            sbuf.append(", ");
            sbuf.append(a[i]);
        }
    }
}
