/*
 * Copyright (c) 2020-2025 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.channel.rs;

import io.netty.util.internal.SystemPropertyUtil;
import org.drasyl.channel.rs.loader.LibraryLoader;
import org.drasyl.crypto.loader.NativeLoader;
import org.drasyl.util.internal.UnstableApi;

import java.io.File;
import java.io.IOException;

import static org.drasyl.crypto.loader.LibraryLoader.PREFER_SYSTEM;

/**
 * This class loads and binds the JNA libdrasyl.
 */
@UnstableApi
public class Libdrasyl {
    public static final long MAX_PEERS_DEFAULT = 128;
    public static final long RECV_BUF_CAP_DEFAULT = 64;

    private static final String DEFAULT_MODE = SystemPropertyUtil.get("drasyl.libdrasyl.mode", PREFER_SYSTEM);

    public Libdrasyl() throws IOException {
        this(DEFAULT_MODE);
    }

    public Libdrasyl(final String loadingMode) throws IOException {
        new LibraryLoader(Libdrasyl.class).loadLibrary(loadingMode, "drasyl");
        register();
    }

    public Libdrasyl(final File libFile) throws IOException {
        try {
            NativeLoader.loadLibraryFromFileSystem(libFile.getAbsolutePath(), Libdrasyl.class);
            register();
        }
        catch (final Exception e) {
            throw new IOException("Could not load local library due to: ", e);
        }
    }

    protected void register() {
        if (drasyl_version() == null) {
            throw new IllegalStateException("Could not initialize drasyl library properly.");
        }
    }

    public static native String drasyl_version();

    public static native int drasyl_generate_identity(byte[] secretKey, byte[] publicKey, byte[] proofOfWork);

    //
    // MessageSink
    //

    public static native int drasyl_recv_buf_len(long bindAddr);

    public static native long drasyl_recv_buf_new(long recvBufCap);

    public static native long drasyl_recv_buf_tx(long recvBufAddr);

    public static native long drasyl_recv_buf_rx(long recvBufAddr);

    public static native int drasyl_recv_buf_recv(long bindAddr, long channelAddr, byte[] sender, byte[] buf, long bufLen);

    public static native int drasyl_recv_buf_free(long recvBufAddr);

    //
    // NodeOptsBuilder
    //

    public static native long drasyl_node_opts_builder_new();

    public static native int drasyl_node_opts_builder_id(long builderAddr, byte[] sk, int pow);

    public static native int drasyl_node_opts_builder_network_id(long builderAddr, int networkId);

    public static native int drasyl_node_opts_builder_message_sink(long builderAddr, long recvBufTxAddr);

    public static native int drasyl_node_opts_builder_udp_port(long builderAddr, int udpPort);

    public static native int drasyl_node_opts_builder_udp_port_none(long builderAddr);

    public static native int drasyl_node_opts_builder_arm_messages(long builderAddr, boolean armMessages);

    public static native int drasyl_node_opts_builder_max_peers(long builderAddr, long maxPeers);

    public static native int drasyl_node_opts_builder_min_pow_difficulty(long builderAddr, byte minPowDifficulty);

    public static native int drasyl_node_opts_builder_hello_timeout(long builderAddr, long helloTimeout);

    public static native int drasyl_node_opts_builder_hello_max_age(long builderAddr, long helloMaxAge);

    public static native int drasyl_node_opts_builder_super_peers(long builderAddr, String superPeers);

    public static native int drasyl_node_opts_builder_process_unites(long builderAddr, boolean processUnites);

    public static native int drasyl_node_opts_builder_housekeeping_interval(long builderAddr, long housekeepingDelay);

    public static native int drasyl_node_opts_builder_build(long builderAddr, byte[] optAddr);

    public static native int drasyl_node_opts_builder_free(long builderAddr);

    //
    // NodeOpts
    //

    public static native int drasyl_node_opts_network_id(long optsAddr);

    public static native int drasyl_node_opts_udp_port(long optsAddr);

    public static native boolean drasyl_node_opts_arm_messages(long optsAddr);

    public static native long drasyl_node_opts_max_peers(long optsAddr);

    public static native byte drasyl_node_opts_min_pow_difficulty(long optsAddr);

    public static native long drasyl_node_opts_hello_timeout(long optsAddr);

    public static native long drasyl_node_opts_hello_max_age(long optsAddr);

    public static native int drasyl_node_opts_mtu(long optsAddr);

    public static native boolean drasyl_node_opts_process_unites(long optsAddr);

    public static native long drasyl_node_opts_housekeeping_interval(long optsAddr);

    public static native int drasyl_node_opts_free(long builderAddr);

    //
    // Node
    //

    public static native int drasyl_node_bind(long optsAddr, byte[] bindAddr);

    public static native int drasyl_node_bind_free(long bindAddr);

    public static native int drasyl_node_send_to(long bindAddr, byte[] recipient, byte[] buf, long bufLen);

    public static native int drasyl_node_peers_list(long bindAddr, byte[] peersListAddr);

    public static native int drasyl_node_udp_port(long bindAddr);

    //
    // PeersList
    //

    public static native int drasyl_peers_list_peers(long peersListAddr, byte[] peersAddr);

    public static native long drasyl_peers_list_peers_len(long peersAddr);

    public static native int drasyl_peers_list_peers_free(long peersAddr);

    public static native int drasyl_peers_list_peer_pk(long peersAddr, int index, byte[] pkAddr);

    public static native int drasyl_peers_list_peer_super_peer(long peersAddr, int index);

    public static native int drasyl_peers_list_peer_reachable(long peersAddr, int index);

    public static int ensureSuccess(int resultCode) {
        if (resultCode < 0) {
            throw new RuntimeException("Unexpected result code " + resultCode);
        }
        return resultCode;
    }
}
