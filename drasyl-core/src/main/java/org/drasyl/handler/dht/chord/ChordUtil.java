package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import io.netty.util.concurrent.PromiseNotifier;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicReference;

import static org.drasyl.util.FutureUtil.chainFuture;
import static org.drasyl.util.FutureUtil.mapFuture;

public final class ChordUtil {
    private static final Logger LOG = LoggerFactory.getLogger(ChordUtil.class);
    public static final long[] POWER_OF_TWO = new long[33];

    static {
        long value = 1;
        for (int i = 0; i < POWER_OF_TWO.length; i++) {
            POWER_OF_TWO[i] = value;
            value *= 2;
        }
    }

    private ChordUtil() {
        // util class
    }

    // 1..32
    public static long ithStart(final long nodeid, final int i) {
        try {
            return (nodeid + POWER_OF_TWO[i - 1]) % POWER_OF_TWO[32];
        }
        catch (final Exception e) {
            e.printStackTrace(System.err);
            return 0;
        }
    }

    public static long hashSocketAddress(final IdentityPublicKey address) {
        final int i = address.hashCode();
        return hashHashCode(i);
//        if (address.toString().equals("d4cb81c941c276ccac03e7d7e1131e1d7f3d00454eb7ee578374b1cfc3990284")) {
//            return 1994087606L;
//        }
//        else if (address.toString().equals("c9e74a44be049d62a743fd1f6efc3e9bdce59691210a9c19eda43ee4c20d3ab6")) {
//            return 3162925264L;
//        }
//        else if (address.toString().equals("e33c1ee9ff871a27f0b75548ca39db696fe690a4bacc30c86541bc3bc882966a")) {
//            return 3173996513L;
//        }
//        else if (address.toString().equals("f63c539b2f1302333bd159698e9bdda904d429968d4266d2180eb1dd3bb417d3")) {
//            return 2017534979L;
//        }
//        else {
//            return ByteBuffer.allocate(Long.BYTES).put(address.toByteArray(), 0, Integer.BYTES).putInt(0).position(0).getInt();
//        }
    }

    /**
     * Compute a 32 bit integer's identifier
     *
     * @param i: integer
     * @return 32-bit identifier in long type
     */
    private static long hashHashCode(final int i) {

        //32 bit regular hash code -> byte[4]
        final byte[] hashbytes = new byte[4];
        hashbytes[0] = (byte) (i >> 24);
        hashbytes[1] = (byte) (i >> 16);
        hashbytes[2] = (byte) (i >> 8);
        hashbytes[3] = (byte) (i /*>> 0*/);

        // try to create SHA1 digest
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch (final NoSuchAlgorithmException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // successfully created SHA1 digest
        // try to convert byte[4]
        // -> SHA1 result byte[]
        // -> compressed result byte[4]
        // -> compressed result in long type
        if (md != null) {
            md.reset();
            md.update(hashbytes);
            final byte[] result = md.digest();

            final byte[] compressed = new byte[4];
            for (int j = 0; j < 4; j++) {
                byte temp = result[j];
                for (int k = 1; k < 5; k++) {
                    temp = (byte) (temp ^ result[j + k]);
                }
                compressed[j] = temp;
            }

            long ret = (compressed[0] & 0xFF) << 24 | (compressed[1] & 0xFF) << 16 | (compressed[2] & 0xFF) << 8 | (compressed[3] & 0xFF);
            ret = ret & (long) 0xFFFFFFFFl;
            return ret;
        }
        return 0;
    }

    public static long hashSocketAddress(final DrasylAddress address) {
        return hashSocketAddress((IdentityPublicKey) address);
    }

    public static String longTo8DigitHex(final long id) {
//        final ByteBuffer buf = ByteBuffer.allocate(Long.BYTES).putLong(id).position(Integer.BYTES);
//        final byte[] a = new byte[buf.position()];
//        buf.get(a);
//        return HexUtil.bytesToHex(a);
        final String hex = Long.toHexString(id);
        final int lack = 8 - hex.length();
        final StringBuilder sb = new StringBuilder();
        for (int i = lack; i > 0; i--) {
            sb.append("0");
        }
        sb.append(hex);
        return sb.toString();
    }

    public static String chordPosition(final long id) {
        return id * 100 / POWER_OF_TWO[32] + "%";
    }

    public static long computeRelativeId(final long universal, final long local) {
        long ret = universal - local;
        if (ret < 0) {
            ret += POWER_OF_TWO[32];
        }
        return ret;
    }

    public static Future<Void> requestKeep(final ChannelHandlerContext ctx,
                                           final IdentityPublicKey peer) {
        final Promise<Void> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordKeepRequestHandler(peer, promise));
        return promise;
    }

    public static Future<IdentityPublicKey> requestYourSuccessor(final ChannelHandlerContext ctx,
                                                                 final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourSuccessorRequestHandler(peer, promise));
        return promise;
    }

    public static Future<IdentityPublicKey> requestClosest(final ChannelHandlerContext ctx,
                                                           final IdentityPublicKey peer,
                                                           final long id) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordClosestRequestHandler(peer, id, promise));
        return promise;
    }

    public static Future<IdentityPublicKey> requestYourPredecessor(final ChannelHandlerContext ctx,
                                                                   final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourPredecessorRequestHandler(peer, promise));
        return promise;
    }

    public static Future<IdentityPublicKey> find_successor(final ChannelHandlerContext ctx,
                                                           final long id,
                                                           final ChordFingerTable fingerTable) {
        LOG.debug("Find successor of `{}`.", longTo8DigitHex(id));

        // initialize return value as this node's successor (might be null)
        final IdentityPublicKey ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", longTo8DigitHex(id));

        // find predecessor
        final Future<IdentityPublicKey> promise = chainFuture(find_predecessor(ctx, id, fingerTable), ctx.executor(), pre -> {
            // if other node found, ask it for its successor
            if (!pre.equals(ctx.channel().localAddress())) {
                return requestYourSuccessor(ctx, pre);
            }
            else {
                return ctx.executor().newSucceededFuture(ret);
            }
        });

        // if ret is still null, set it as local node, return
        return mapFuture(promise, ctx.executor(), ret1 -> {
            if (ret1 == null) {
                return (IdentityPublicKey) ctx.channel().localAddress();
            }
            return ret1;
        });
    }

    private static Future<IdentityPublicKey> find_predecessor(final ChannelHandlerContext ctx,
                                                              final long findid,
                                                              final ChordFingerTable fingerTable) {
        LOG.debug("Find predecessor of `{}`", longTo8DigitHex(findid));
        final IdentityPublicKey n = (IdentityPublicKey) ctx.channel().localAddress();
        final IdentityPublicKey n_successor = fingerTable.getSuccessor();
        long n_successor_relative_id = 0;
        if (n_successor != null) {
            n_successor_relative_id = computeRelativeId(hashSocketAddress(n_successor), hashSocketAddress(n));
        }
        final long findid_relative_id = computeRelativeId(findid, hashSocketAddress(n));

        return find_predecessor_recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id, (IdentityPublicKey) ctx.channel().localAddress(), fingerTable).addListener(new FutureListener<>() {
            @Override
            public void operationComplete(final Future<IdentityPublicKey> future) {
                LOG.debug("Predecessor of `{}` is `{}`", longTo8DigitHex(findid), future.getNow());
            }
        });
    }

    private static Future<IdentityPublicKey> find_predecessor_recursive(final ChannelHandlerContext ctx,
                                                                        final long findid,
                                                                        final IdentityPublicKey pre_n,
                                                                        final long findid_relative_id,
                                                                        final long n_successor_relative_id,
                                                                        final IdentityPublicKey most_recently_alive,
                                                                        final ChordFingerTable fingerTable) {
        if (findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id) {
            return ctx.executor().newSucceededFuture(pre_n);
        }

        // if current node is local node, find my closest
        if (pre_n.equals(ctx.channel().localAddress())) {
            return chainFuture(closest_preceding_finger(ctx, findid, fingerTable), ctx.executor(), n -> {
                if (pre_n.equals(n)) {
                    return ctx.executor().newSucceededFuture(n);
                }
                else {
                    return find_predecessor_recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                }
            });
        }
        // else current node is remote node, sent request to it for its closest
        else {
            return chainFuture(requestClosest(ctx, pre_n, findid), ctx.executor(), result -> {
                // if fail to get response, set n to most recently
                if (result == null) {
                    return chainFuture(requestYourSuccessor(ctx, most_recently_alive), ctx.executor(), n_successor -> {
                        if (n_successor == null) {
                            return ctx.executor().newSucceededFuture((IdentityPublicKey) ctx.channel().localAddress());
                        }
                        return find_predecessor_recursive(ctx, findid, most_recently_alive, findid_relative_id, n_successor_relative_id, most_recently_alive, fingerTable);
                    });
                }

                // if n's closest is itself, return n
                else if (result.equals(pre_n)) {
                    return ctx.executor().newSucceededFuture(result);
                }

                // else n's closest is other node "result"
                else {
                    // set n as most recently alive
                    // ask "result" for its successor
                    return chainFuture(requestYourSuccessor(ctx, result), ctx.executor(), n_successor -> {
                        // if we can get its response, then "result" must be our next n
                        if (n_successor != null) {
                            if (pre_n.equals(result)) {
                                return ctx.executor().newSucceededFuture(result);
                            }

                            // compute relative ids for while loop judgement
                            final long n_successor_relative_id2 = computeRelativeId(hashSocketAddress(n_successor), hashSocketAddress(result));
                            final long findid_relative_id2 = computeRelativeId(findid, hashSocketAddress(result));

                            return find_predecessor_recursive(ctx, findid, result, findid_relative_id2, n_successor_relative_id2, pre_n, fingerTable);
                        }
                        // else n sticks, ask n's successor
                        else {
                            return requestYourSuccessor(ctx, pre_n);
                        }
                    });
                }
            });
        }
    }

    public static Future<IdentityPublicKey> closest_preceding_finger(final ChannelHandlerContext ctx,
                                                                     final long findid,
                                                                     final ChordFingerTable fingerTable) {
        LOG.debug("Find closest finger preceding `{}`.", longTo8DigitHex(findid));
        final long localId = hashSocketAddress((IdentityPublicKey) ctx.channel().localAddress());
        final long findid_relative = computeRelativeId(findid, localId);

        // check from last item in finger table
        return closest_preceding_finger_recursive(ctx, findid, localId, findid_relative, 32, fingerTable);
    }

    private static Future<IdentityPublicKey> closest_preceding_finger_recursive(final ChannelHandlerContext ctx,
                                                                                final long findid,
                                                                                final long localId,
                                                                                final long findid_relative,
                                                                                final int i,
                                                                                final ChordFingerTable fingerTable) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", longTo8DigitHex(findid));
            return ctx.executor().newSucceededFuture((IdentityPublicKey) ctx.channel().localAddress());
        }

        final IdentityPublicKey ith_finger = fingerTable.get(i);
        if (ith_finger != null) {
            final long ith_finger_id = hashSocketAddress(ith_finger);
            final long ith_finger_relative_id = computeRelativeId(ith_finger_id, localId);

            // if its relative id is the closest, check if its alive
            if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative) {
                LOG.debug("{}th finger {} is closest preceding finger of {}.", i, longTo8DigitHex(ith_finger_id), longTo8DigitHex(findid));
                LOG.debug("Check if it is still alive.");
                final Promise<IdentityPublicKey> objectPromise = ctx.executor().newPromise();
                requestKeep(ctx, ith_finger).addListener((FutureListener<Void>) future -> {
                    //it is alive, return it
                    if (future.cause() == null) {
                        LOG.debug("Peer is still alive.");
                        objectPromise.setSuccess(ith_finger);
                    }

                    // else, remove its existence from finger table
                    else {
                        LOG.warn("Peer is not alive. Remove it from finger table.");
                        fingerTable.removePeer(ith_finger);
                        closest_preceding_finger_recursive(ctx, findid, localId, findid_relative, i - 1, fingerTable).addListener(new PromiseNotifier<>(objectPromise));
                    }
                });
                return objectPromise;
            }
        }
        return closest_preceding_finger_recursive(ctx, findid, localId, findid_relative, i - 1, fingerTable);
    }

    public static Future<Void> requestIAmPre(final ChannelHandlerContext ctx,
                                             final IdentityPublicKey peer) {
        final Promise<Void> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordIAmPreRequestHandler(peer, promise));
        return promise;
    }

    /**
     * Try to fill successor with candidates in finger table or even predecessor
     */
    public static Future<Void> fillSuccessor(final ChannelHandlerContext ctx,
                                             final AtomicReference<IdentityPublicKey> predecessor,
                                             final ChordFingerTable fingerTable) {
        LOG.debug("Try to fill successor with candidates in finger table or even predecessor.");
        final IdentityPublicKey successor = fingerTable.getSuccessor();
        final Future<Void> future;
        if (successor == null || successor.equals(ctx.channel().localAddress())) {
            future = fillSuccessor_recursive(ctx, 2, fingerTable);
        }
        else {
            future = ctx.executor().newSucceededFuture(null);
        }

        return chainFuture(future, ctx.executor(), unused -> {
            final IdentityPublicKey successor2 = fingerTable.getSuccessor();
            if ((successor2 == null || successor2.equals(ctx.channel().localAddress())) && predecessor.get() != null && !predecessor.get().equals(ctx.channel().localAddress())) {
                return fingerTable.updateIthFinger(ctx, 1, predecessor.get());
            }
            else {
                return ctx.executor().newSucceededFuture(null);
            }
        });
    }

    private static Future<Void> fillSuccessor_recursive(final ChannelHandlerContext ctx,
                                                        final int i,
                                                        final ChordFingerTable fingerTable) {
        if (i <= 32) {
            final IdentityPublicKey ithfinger = fingerTable.get(i);
            if (ithfinger != null && !ithfinger.equals(ctx.channel().localAddress())) {
                return fillSuccessor_recursive2(ctx, i - 1, ithfinger, fingerTable);
            }
            else {
                return fillSuccessor_recursive(ctx, i + 1, fingerTable);
            }
        }
        else {
            return ctx.executor().newSucceededFuture(null);
        }
    }

    private static Future<Void> fillSuccessor_recursive2(final ChannelHandlerContext ctx,
                                                         final int j,
                                                         final IdentityPublicKey ithfinger,
                                                         final ChordFingerTable fingerTable) {
        if (j >= 1) {
            return chainFuture(fingerTable.updateIthFinger(ctx, j, ithfinger), ctx.executor(), unused -> {
                return fillSuccessor_recursive2(ctx, j - 1, ithfinger, fingerTable);
            });
        }
        else {
            return ctx.executor().newSucceededFuture(null);
        }
    }

    public static long hashString(final String s) {
        final int i = s.hashCode();
        return hashHashCode(i);
    }

    public static Future<IdentityPublicKey> requestFindSuccessor(final ChannelHandlerContext ctx,
                                                                 final long id,
                                                                 final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordFindSuccessorRequestHandler(peer, id, promise));
        return promise;
    }
}
