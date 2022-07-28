package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.UnexecutableFutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFuture;
import static org.drasyl.util.UnexecutableFutureComposer.composeUnexecutableFuture;

public class ChordKeepRequester extends AbstractChordRequester<Alive, Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordKeepRequester.class);

    public ChordKeepRequester(final IdentityPublicKey peer,
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

    public static UnexecutableFutureComposer<Void> keepRequest(final ChannelHandlerContext ctx,
                                                                              final IdentityPublicKey peer) {
        final Promise<Void> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordKeepRequester(peer, promise));
        return composeUnexecutableFuture().thenUnexecutable(composeFuture(ctx.executor(), promise));
    }
}
