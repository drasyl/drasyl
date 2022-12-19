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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadFactory;

/**
 * Utility class that can be used to detect properties specific to the current runtime environment,
 * such as Java version and the availability of the {@code sun.misc.Unsafe} object.
 */
public final class PlatformDependent {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformDependent.class);
    private static final float JAVA_VERSION = detectJavaVersion();
    // See https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/
    // ImageInfo.java
    private static final boolean RUNNING_IN_NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

    private PlatformDependent() {
        // util class
    }

    /**
     * Returns the Java version.
     *
     * @return the Java version
     */
    public static float javaVersion() {
        return JAVA_VERSION;
    }

    /**
     * Returns {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise.
     *
     * @return {@code true} if access to {@link sun.misc.Unsafe#staticFieldOffset(Field)} is
     * supported, {@code false} otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public static boolean unsafeStaticFieldOffsetSupported() {
        return !RUNNING_IN_NATIVE_IMAGE;
    }

    public static EventLoopGroup getBestEventLoopGroup(final int nThreads, final ThreadFactory threadFactory) {
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(nThreads, threadFactory);
        }
        else if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(nThreads, threadFactory);
        }
        else {
            return new NioEventLoopGroup(nThreads, threadFactory);
        }
    }

    public static EventLoopGroup getBestEventLoopGroup(final int nThreads) {
        return getBestEventLoopGroup(nThreads, null);
    }

    public static Class<? extends DatagramChannel> getBestDatagramChannel() {
        if (Epoll.isAvailable()) {
            return EpollDatagramChannel.class;
        }
        else if (KQueue.isAvailable()) {
            return KQueueDatagramChannel.class;
        }
        else {
            return NioDatagramChannel.class;
        }
    }

    public static DatagramChannel getBestDatagramChannel(final InternetProtocolFamily family) {
        if (Epoll.isAvailable()) {
            return new EpollDatagramChannel(family);
        }
        else if (KQueue.isAvailable()) {
            return new KQueueDatagramChannel(family);
        }
        else {
            return new NioDatagramChannel(family);
        }
    }

    static float javaSpecificationVersion() {
        return Float.parseFloat(System.getProperty("java.specification.version", "11"));
    }

    private static float detectJavaVersion() {
        final float majorVersion = javaSpecificationVersion();

        LOG.debug("Java version: {}", majorVersion);

        return majorVersion;
    }
}
