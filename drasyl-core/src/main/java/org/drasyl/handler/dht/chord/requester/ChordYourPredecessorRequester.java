package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.NothingPredecessor;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordYourPredecessorRequester extends AbstractChordRequester<ChordMessage, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordYourPredecessorRequester.class);

    public ChordYourPredecessorRequester(final IdentityPublicKey peer,
                                         final Promise<IdentityPublicKey> promise) {
        super(YourPredecessor.of(), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MyPredecessor || msg instanceof NothingPredecessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final ChordMessage response,
                                  final Promise<IdentityPublicKey> promise) {
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

    public static FutureComposer<IdentityPublicKey> yourPredecessorRequest(final ChannelHandlerContext ctx,
                                                                           final IdentityPublicKey peer) {
        final Promise<IdentityPublicKey> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourPredecessorRequester(peer, promise));
        return composeFuture(ctx.executor(), promise);
    }
}
