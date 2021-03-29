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

import io.netty.util.ReferenceCounted;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public final class ReferenceCountUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceCountUtil.class);

    private ReferenceCountUtil() {
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link
     * ReferenceCounted} and is not already released. If the specified message doesn't implement
     * {@link ReferenceCounted}, this method does nothing.
     */
    public static boolean release(final Object o) {
        if (o instanceof ReferenceCounted) {
            final ReferenceCounted ref = (ReferenceCounted) o;

            if (ref.refCnt() != 0) {
                return ref.release();
            }

            return true;
        }

        return false;
    }

    /**
     * Try to call {@link ReferenceCounted#release()} if the specified message implements {@link
     * ReferenceCounted} and is not already released. If the specified message doesn't implement
     * {@link ReferenceCounted}, this method does nothing. Unlike {@link #release(Object)} this
     * method catches an exception raised by {@link ReferenceCounted#release()} and logs it, rather
     * than rethrowing it to the caller.  It is usually recommended to use {@link #release(Object)}
     * instead, unless you absolutely need to swallow an exception.
     */
    @SuppressWarnings({ "java:S1181" })
    public static void safeRelease(final Object o) {
        try {
            release(o);
        }
        catch (final Throwable t) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Failed to release a reference counted object: {}", o, t);
            }
        }
    }
}
