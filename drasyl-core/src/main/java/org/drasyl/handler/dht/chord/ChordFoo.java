package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.Nothing;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

public class ChordFoo extends SimpleChannelInboundHandler<OverlayAddressedMessage<ChordMessage>> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFoo.class);
    private final AtomicReference<IdentityPublicKey> predecessor;
    private final ChordFingerTable fingerTable;

    public ChordFoo(final AtomicReference<IdentityPublicKey> predecessor,
                    final ChordFingerTable fingerTable) {
        this.predecessor = requireNonNull(predecessor);
        this.fingerTable = requireNonNull(fingerTable);
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final OverlayAddressedMessage<ChordMessage> msg) throws Exception {
        if (msg.content() instanceof Closest) {
            final long id = ((Closest) msg.content()).getId();
            closest_preceding_finger(ctx, id).addListener((FutureListener<IdentityPublicKey>) future -> {
                final IdentityPublicKey result = future.get();
                final OverlayAddressedMessage<MyClosest> response = new OverlayAddressedMessage<>(MyClosest.of(result), msg.sender());
                ctx.writeAndFlush(response);
            });
        }
        else if (msg.content() instanceof YourSuccessor) {
            if (fingerTable.getSuccessor() != null) {
                final OverlayAddressedMessage<MySuccessor> response = new OverlayAddressedMessage<>(MySuccessor.of(fingerTable.getSuccessor()), msg.sender());
                ctx.writeAndFlush(response);
            }
            else {
                final OverlayAddressedMessage<Nothing> response = new OverlayAddressedMessage<>(Nothing.of(), msg.sender());
                ctx.writeAndFlush(response);
            }
        }
        else if (msg.content() instanceof YourPredecessor) {
            if (predecessor.get() != null) {
                final OverlayAddressedMessage<MyPredecessor> response = new OverlayAddressedMessage<>(MyPredecessor.of(predecessor.get()), msg.sender());
                ctx.writeAndFlush(response);
            }
            else {
                final OverlayAddressedMessage<Nothing> response = new OverlayAddressedMessage<>(Nothing.of(), msg.sender());
                ctx.writeAndFlush(response);
            }
        }
        else if (msg.content() instanceof FindSuccessor) {
            final long id = ((FindSuccessor) msg.content()).getId();

            find_successor(ctx, id).addListener((FutureListener<IdentityPublicKey>) future -> {
                final OverlayAddressedMessage<FoundSuccessor> response = new OverlayAddressedMessage<>(FoundSuccessor.of(future.get()), msg.sender());
                ctx.writeAndFlush(response);
            });
        }
        else if (msg.content() instanceof IAmPre) {
            final IdentityPublicKey newpre = ((IAmPre) msg.content()).getAddress();
            notified(ctx, newpre, msg.sender());
        }
        else if (msg.content() instanceof Keep) {
            final OverlayAddressedMessage<Alive> response = new OverlayAddressedMessage<>(Alive.of(), msg.sender());
            ctx.writeAndFlush(response);
        }
        else {
            ctx.fireChannelRead(msg);
        }
    }

    private void notified(final ChannelHandlerContext ctx,
                          final IdentityPublicKey newpre,
                          final DrasylAddress sender) {
        LOG.debug("Notified by `{}`.", newpre);
        if (predecessor.get() == null || predecessor.get().equals(ctx.channel().localAddress())) {
            predecessor.set(newpre);
        }
        else {
            final long oldpre_id = ChordUtil.chordId(predecessor.get());
            final long local_relative_id = ChordUtil.computeRelativeId(ChordUtil.chordId((IdentityPublicKey) ctx.channel().localAddress()), oldpre_id);
            final long newpre_relative_id = ChordUtil.computeRelativeId(ChordUtil.chordId(newpre), oldpre_id);
            if (newpre_relative_id > 0 && newpre_relative_id < local_relative_id) {
                predecessor.set(newpre);
            }
        }

        ctx.writeAndFlush(new OverlayAddressedMessage<>(Notified.of(), sender));
    }

    private Future<IdentityPublicKey> find_successor(final ChannelHandlerContext ctx,
                                                     final long id) {
        LOG.debug("Find successor of `{}`.", ChordUtil.chordIdToHex(id));

        // initialize return value as this node's successor (might be null)
        final IdentityPublicKey ret = fingerTable.getSuccessor();

        LOG.debug("Find successor of {} by asking id's predecessor for its successor.", ChordUtil.chordIdToHex(id));

        // find predecessor
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        find_predecessor(ctx, id).addListener((FutureListener<IdentityPublicKey>) preFuture -> {
            final IdentityPublicKey pre = preFuture.get();

            // if other node found, ask it for its successor
            if (!pre.equals(ctx.channel().localAddress())) {
                requestYourSuccessor(ctx, pre).addListener((FutureListener<IdentityPublicKey>) retFuture -> {
                    if (retFuture.cause() == null) {
                        promise.setSuccess(retFuture.get());
                    }
                    else {
                        promise.setFailure(retFuture.cause());
                    }
                });
            }
            else {
                promise.setSuccess(ret);
            }
        });

        Futures.

        // if ret is still null, set it as local node, return
        final Promise<IdentityPublicKey> promise2 = ctx.executor().newPromise();
        promise.addListener((FutureListener<IdentityPublicKey>) future -> {
            if (future.get() == null) {
                promise2.setSuccess((IdentityPublicKey) ctx.channel().localAddress());
            }
            else {
                promise2.setFailure(future.cause());
            }
        });

        return promise2;
    }

    private Future<IdentityPublicKey> requestYourSuccessor(final ChannelHandlerContext ctx,
                                                           final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourSuccessorRequestHandler(peer, promise));
        return promise;
    }

    private Future<IdentityPublicKey> find_predecessor(final ChannelHandlerContext ctx,
                                                       final long findid) {
        LOG.debug("Find predecessor of {}", ChordUtil.chordIdToHex(findid));
        final IdentityPublicKey n = (IdentityPublicKey) ctx.channel().localAddress();
        final IdentityPublicKey n_successor = fingerTable.getSuccessor();
        long n_successor_relative_id = 0;
        if (n_successor != null) {
            n_successor_relative_id = ChordUtil.computeRelativeId(ChordUtil.chordId(n_successor), ChordUtil.chordId(n));
        }
        final long findid_relative_id = ChordUtil.computeRelativeId(findid, ChordUtil.chordId(n));

        return find_predecessor_recursive(ctx, findid, n, findid_relative_id, n_successor_relative_id);
    }

    private Future<IdentityPublicKey> find_predecessor_recursive(final ChannelHandlerContext ctx,
                                                                 final long findid,
                                                                 final IdentityPublicKey n,
                                                                 final long findid_relative_id,
                                                                 final long n_successor_relative_id) {
        final IdentityPublicKey most_recently_alive = (IdentityPublicKey) ctx.channel().localAddress();

        if ((!(findid_relative_id > 0 && findid_relative_id <= n_successor_relative_id))) {
            // temporarily save current node
            final IdentityPublicKey pre_n = n;

            // if current node is local node, find my closest
            final Promise<IdentityPublicKey> nPromise = ctx.executor().newPromise();
            if (n.equals(ctx.channel().localAddress())) {
                closest_preceding_finger(ctx, findid).addListener((FutureListener<IdentityPublicKey>) future -> {
                    if (pre_n.equals(future.get())) {
                        nPromise.setSuccess(future.get());
                    }
                    else {
                        find_predecessor_recursive(ctx, findid, future.get(), findid_relative_id, n_successor_relative_id).addListener((FutureListener<IdentityPublicKey>) future1 -> {
                            if (future1.cause() == null) {
                                nPromise.setSuccess(future1.get());
                            }
                            else {
                                nPromise.setFailure(future1.cause());
                            }
                        });
                    }
                });
            }
            // else current node is remote node, sent request to it for its closest
            else {
                // FIXME: REQ_CLOSEST

                if (pre_n.equals(n)) {
                    return ctx.executor().newSucceededFuture(n);
                }
            }
            return nPromise;
        }

        return ctx.executor().newSucceededFuture(n);
    }

    private Future<IdentityPublicKey> requestClosest(final ChannelHandlerContext ctx,
                                                     final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordClosestRequestHandler(peer, promise));
        return promise;
    }

    private Future<IdentityPublicKey> closest_preceding_finger(final ChannelHandlerContext ctx,
                                                               final long findid) {
        LOG.debug("Find closest finger preceding `{}`.", ChordUtil.chordIdToHex(findid));
        final long localId = ChordUtil.chordId((IdentityPublicKey) ctx.channel().localAddress());
        final long findid_relative = ChordUtil.computeRelativeId(findid, localId);

        // check from last item in finger table
        return closest_preceding_finger_recursive(ctx, findid, localId, findid_relative, 32);
    }

    private Future<IdentityPublicKey> closest_preceding_finger_recursive(final ChannelHandlerContext ctx,
                                                                         final long findid,
                                                                         final long localId,
                                                                         final long findid_relative,
                                                                         final int i) {
        if (i == 0) {
            LOG.debug("We're closest to `{}`.", ChordUtil.chordIdToHex(findid));
            return ctx.executor().newSucceededFuture((IdentityPublicKey) ctx.channel().localAddress());
        }

        final IdentityPublicKey ith_finger = fingerTable.get(i);
        if (ith_finger != null) {
            final long ith_finger_id = ChordUtil.chordId(ith_finger);
            final long ith_finger_relative_id = ChordUtil.computeRelativeId(ith_finger_id, localId);

            // if its relative id is the closest, check if its alive
            if (ith_finger_relative_id > 0 && ith_finger_relative_id < findid_relative) {
                LOG.debug("{}th finger {} is closest preceding finger of {}.", i, ChordUtil.chordIdToHex(ith_finger_id), ChordUtil.chordIdToHex(findid));
                // FIXME: alive check skipped
                LOG.debug("Peer is still alive.");
                return ctx.executor().newSucceededFuture(ith_finger);
            }
        }
        return closest_preceding_finger_recursive(ctx, findid, localId, findid_relative, i - 1);
    }
}
