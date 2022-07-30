package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.ChordException;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.NothingPredecessor;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;

/**
 * Sends a {@link YourPredecessor} message to given {@link #peer}.
 */
public class ChordYourPredecessorRequester extends AbstractChordRequester<ChordMessage, DrasylAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordYourPredecessorRequester.class);

    public ChordYourPredecessorRequester(final DrasylAddress peer,
                                         final Promise<DrasylAddress> promise) {
        super(YourPredecessor.of(), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MyPredecessor || msg instanceof NothingPredecessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final ChordMessage response,
                                  final Promise<DrasylAddress> promise) {
        if (response instanceof MyPredecessor) {
            promise.trySuccess(((MyPredecessor) response).getAddress());
        }
        else {
            promise.trySuccess(null);
        }
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    public static FutureComposer<DrasylAddress> requestPredecessor(final ChannelHandlerContext ctx,
                                                                   final DrasylAddress peer) {
        if (peer == null) {
            return composeFailedFuture(new ChordException("peer is null"));
        }
        final Promise<DrasylAddress> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourPredecessorRequester(peer, promise));
        return composeFuture().chain(promise);
    }
}
