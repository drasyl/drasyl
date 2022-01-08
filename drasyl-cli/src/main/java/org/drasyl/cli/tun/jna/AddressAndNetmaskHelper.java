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
package org.drasyl.cli.tun.jna;

import com.sun.jna.LastErrorException;
import com.sun.jna.Pointer;
import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.tun.jna.windows.WinDef.DWORD;
import org.drasyl.channel.tun.jna.windows.loader.LibraryLoader;

import java.io.IOException;

import static org.drasyl.channel.tun.jna.windows.loader.LibraryLoader.PREFER_SYSTEM;

/**
 * JNA based helper class to set the IP address and netmask for a given network device.
 */
public final class AddressAndNetmaskHelper {
    private static final String DEFAULT_MODE = SystemPropertyUtil.get("tun.native.mode", PREFER_SYSTEM);

    static {
        try {
            new LibraryLoader(AddressAndNetmaskHelper.class).loadLibrary(DEFAULT_MODE, "libdtun");
        }
        catch (final IOException e) {
            throw new RuntimeException(e); // NOSONAR
        }
    }

    private AddressAndNetmaskHelper() {
        // JNA mapping
    }

    public static native DWORD setIPv4AndNetmask(final Pointer luid,
                                                 final String ip,
                                                 final int mask) throws LastErrorException;

    public static native DWORD setIPv6AndNetmask(final Pointer luid,
                                                 final String ip,
                                                 final int mask) throws LastErrorException;
}
