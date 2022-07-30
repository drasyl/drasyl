package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.ChordException;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * Sends a {@link Keep} message to given {@link #peer}.
 */
public class ChordKeepRequester extends AbstractChordRequester<Alive, Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordKeepRequester.class);

    public ChordKeepRequester(final DrasylAddress peer,
                              final Promise<Void> promise) {
        super(Keep.of(), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof Alive;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final Alive response,
                                  final Promise<Void> promise) {
        promise.trySuccess(null);
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    public static FutureComposer<Void> keepRequest(final ChannelHandlerContext ctx,
                                                   final DrasylAddress peer) {
        if (peer == null) {
            return composeFailedFuture(new ChordException("peer is null"));
        }
        final Promise<Void> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordKeepRequester(peer, promise));
        return composeFuture().chain(promise);
    }
}
