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
package org.drasyl.cli.tunrc;

import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.rc.AbstractRcSubcommand;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;

@Command(
        name = "remove-route",
        description = {
                "Removes a route."
        }
)
public class TunRcRemoveRouteCommand extends AbstractRcSubcommand {
    private static final Logger LOG = LoggerFactory.getLogger(TunRcRemoveRouteCommand.class);
    @Option(
            names = { "--public-key" },
            description = "Public key of the peer.",
            paramLabel = "<public-key>",
            required = true
    )
    private IdentityPublicKey publicKey;
    @Option(
            names = { "--address" },
            description = {
                    "IP address of the peer."
            },
            paramLabel = "<address>"
    )
    private InetAddress address;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected JsonRpc2Request getRequest() throws IOException {
        return new JsonRpc2Request("removeRoute", Map.of(
                "publicKey", publicKey.toString(),
                "address", address != null ? address.getHostAddress() : ""
        ));
    }
}
