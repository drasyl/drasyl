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
package org.drasyl.cli.tunnel;

import org.drasyl.cli.converter.InetSocketAddressConverter;
import picocli.CommandLine.ITypeConverter;

import java.net.InetSocketAddress;

public class TunnelServiceConverter implements ITypeConverter<InetSocketAddress> {
    private static final ITypeConverter<InetSocketAddress> ADDRESS_CONVERTER = new InetSocketAddressConverter();

    @Override
    public InetSocketAddress convert(final String value) throws Exception {
        final int portSeparator = value.lastIndexOf(':');
        if (portSeparator == -1) {
            // port-only
            return new InetSocketAddress("127.0.0.1", Integer.valueOf(value));
        }
        else {
            // address + port
            final InetSocketAddress address = ADDRESS_CONVERTER.convert(value);

            if (address.getPort() == 0) {
                throw new IllegalArgumentException("port missing!");
            }

            return address;
        }
    }
}
