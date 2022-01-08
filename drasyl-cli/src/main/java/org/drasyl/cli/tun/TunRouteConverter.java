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
package org.drasyl.cli.tun;

import org.drasyl.identity.IdentityPublicKey;
import picocli.CommandLine.ITypeConverter;

import java.net.InetAddress;

/**
 * Converts command line argument values to {@link TunRoute}s.
 */
public class TunRouteConverter implements ITypeConverter<TunRoute> {
    @Override
    public TunRoute convert(final String value) throws Exception {
        if (value.indexOf('=') == -1) {
            final IdentityPublicKey overlayAddress = IdentityPublicKey.of(value);
            return new TunRoute(overlayAddress);
        }
        else {
            final IdentityPublicKey overlayAddress = IdentityPublicKey.of(value.substring(0, value.indexOf('=')));
            final InetAddress inetAddress = InetAddress.getByName(value.substring(value.indexOf('=')));
            return new TunRoute(overlayAddress, inetAddress);
        }
    }
}
