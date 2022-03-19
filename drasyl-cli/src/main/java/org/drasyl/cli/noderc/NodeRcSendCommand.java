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
package org.drasyl.cli.noderc;

import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.crypto.HexUtil;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.util.Map;

@Command(
        name = "send",
        description = {
                "Sends a message."
        }
)
public class NodeRcSendCommand extends AbstractNodeRcSubcommand {
    private static final Logger LOG = LoggerFactory.getLogger(NodeRcSendCommand.class);
    @Option(
            names = { "--recipient" },
            description = "Recipient of the message.",
            paramLabel = "<public-key>",
            required = true
    )
    private IdentityPublicKey recipient;
    @ArgGroup(exclusive = true, multiplicity = "1")
    private Payload payload;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected JsonRpc2Request getRequest() throws IOException {
        final Object payload;
        if (this.payload.string != null) {
            payload = this.payload.string;
        }
        else {
            payload = HexUtil.fromString(this.payload.hexString);
        }

        return new JsonRpc2Request("send", Map.of(
                "recipient", recipient.toString(),
                "payload", payload
        ));
    }

    static class Payload {
        @Option(
                names = { "--text" },
                description = "String to be used as message payload.",
                paramLabel = "<text>"
        )
        private String string;
        @Option(
                names = { "--hex" },
                description = "Bytes as hex string to be used as message payload.",
                paramLabel = "<bytes>"
        )
        private String hexString;
    }
}
