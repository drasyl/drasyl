/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package test.util;

import org.drasyl.DrasylConfig;

import static java.util.stream.Collectors.toSet;
import static org.drasyl.DrasylConfig.IDENTITY_PRIVATE_KEY;
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
        if (config.getIdentityPrivateKey() != null) {
            builder.append(IDENTITY_PRIVATE_KEY + " = ").append(config.getIdentityPrivateKey().toString()).append("\n");
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
