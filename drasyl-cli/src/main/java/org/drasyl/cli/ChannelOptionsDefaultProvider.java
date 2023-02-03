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
package org.drasyl.cli;

import org.drasyl.identity.IdentityPublicKey;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

import java.net.InetSocketAddress;
import java.util.Map.Entry;

import static org.drasyl.channel.RelayOnlyDrasylServerChannelInitializer.SUPER_PEERS;

public class ChannelOptionsDefaultProvider implements CommandLine.IDefaultValueProvider {
    @Override
    public String defaultValue(ArgSpec argSpec) throws Exception {
        if (argSpec.isOption()) {
            final OptionSpec optionSpec = (OptionSpec) argSpec;
            if (optionSpec.names().length > 0 && "--super-peers".equals(optionSpec.names()[0])) {
                final StringBuilder builder = new StringBuilder();
                boolean first = true;
                for (Entry<IdentityPublicKey, InetSocketAddress> entry : SUPER_PEERS.entrySet()) {
                    if (first) {
                        first = false;
                    }
                    else {
                        builder.append(",");
                    }
                    final IdentityPublicKey publicKey = entry.getKey();
                    final InetSocketAddress inetAddress = entry.getValue();
                    builder.append(publicKey.toString());
                    builder.append("=");
                    builder.append(inetAddress.getHostString());
                    builder.append(":");
                    builder.append(inetAddress.getPort());
                }
                return builder.toString();
            }
        }
        return null;
    }
}
