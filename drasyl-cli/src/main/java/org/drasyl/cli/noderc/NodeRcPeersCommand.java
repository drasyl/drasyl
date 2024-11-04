/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.rc.AbstractRcSubcommand;
import org.drasyl.serialization.PeerMixin;
import org.drasyl.serialization.PeersListMixin;
import org.drasyl.serialization.RoleMixin;
import org.drasyl.handler.peers.Peer;
import org.drasyl.handler.peers.PeersList;
import org.drasyl.handler.peers.Role;
import org.drasyl.serialization.DrasylAddressMixin;
import org.drasyl.serialization.IdentityPublicKeyMixin;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.drasyl.node.JsonUtil.JACKSON_MAPPER;

@Command(
        name = "peers",
        description = {
                "Requests the peers list."
        }
)
public class NodeRcPeersCommand extends AbstractRcSubcommand {
    private static final Logger LOG = LoggerFactory.getLogger(NodeRcPeersCommand.class);

    @Option(
            names = { "--pretty" },
            description = {
                    "Print pretty peers list rather than using JSON."
            }
    )
    protected boolean pretty;

    @Override
    protected Logger log() {
        return LOG;
    }

    @Override
    protected JsonRpc2Request getRequest() {
        return new JsonRpc2Request("peers");
    }

    @Override
    protected void printResult(final Object result) throws JsonProcessingException {
        if (!pretty) {
            super.printResult(result);
        }
        else {
            JACKSON_MAPPER.addMixIn(PeersList.class, PeersListMixin.class);
            JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
            JACKSON_MAPPER.addMixIn(DrasylAddress.class, DrasylAddressMixin.class);
            JACKSON_MAPPER.addMixIn(Peer.class, PeerMixin.class);
            JACKSON_MAPPER.addMixIn(Role.class, RoleMixin.class);

            final PeersList peersList = JACKSON_MAPPER.convertValue(result, new TypeReference<>() {
            });
            System.out.println(peersList);
        }
    }
}
