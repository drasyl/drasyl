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
package test.util;

import org.drasyl.DrasylConfig;

import static java.util.stream.Collectors.toSet;
import static org.drasyl.DrasylConfig.IDENTITY_SECRET_KEY;
import static org.drasyl.DrasylConfig.IDENTITY_PROOF_OF_WORK;
import static org.drasyl.DrasylConfig.IDENTITY_PUBLIC_KEY;
import static org.drasyl.DrasylConfig.INTRA_VM_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.NETWORK_ID;
import static org.drasyl.DrasylConfig.REMOTE_BIND_HOST;
import static org.drasyl.DrasylConfig.REMOTE_BIND_PORT;
import static org.drasyl.DrasylConfig.REMOTE_EXPOSE_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_LOCAL_HOST_DISCOVERY_ENABLED;
import static org.drasyl.DrasylConfig.REMOTE_SUPER_PEER_ENDPOINTS;

public final class DrasylConfigRenderer {
    private DrasylConfigRenderer() {
        // util class
    }

    public static String renderConfig(final DrasylConfig config) {
        final StringBuilder builder = new StringBuilder();
        builder.append(NETWORK_ID + " = ").append(config.getNetworkId()).append("\n");
        if (config.getIdentityProofOfWork() != null) {
            builder.append(IDENTITY_PROOF_OF_WORK + " = ").append(config.getIdentityProofOfWork().intValue()).append("\n");
        }
        if (config.getIdentityPublicKey() != null) {
            builder.append(IDENTITY_PUBLIC_KEY + " = ").append(config.getIdentityPublicKey().toString()).append("\n");
        }
        if (config.getIdentitySecretKey() != null) {
            builder.append(IDENTITY_SECRET_KEY + " = ").append(config.getIdentitySecretKey().toUnmaskedString()).append("\n");
        }
        builder.append(REMOTE_BIND_HOST + " = ").append(config.getRemoteBindHost().getHostAddress()).append("\n");
        builder.append(REMOTE_BIND_PORT + " = ").append(config.getRemoteBindPort()).append("\n");
        builder.append(REMOTE_SUPER_PEER_ENDPOINTS + " = [\"").append(String.join("\", \"", config.getRemoteSuperPeerEndpoints().stream().map(Object::toString).collect(toSet()))).append("\"]\n");
        builder.append(REMOTE_LOCAL_HOST_DISCOVERY_ENABLED + " = ").append(config.isRemoteLocalHostDiscoveryEnabled()).append("\n");
        builder.append(REMOTE_EXPOSE_ENABLED + " = ").append(config.isRemoteExposeEnabled()).append("\n");
        builder.append(INTRA_VM_DISCOVERY_ENABLED + " = ").append(config.isIntraVmDiscoveryEnabled()).append("\n");
        return builder.toString();
    }
}
