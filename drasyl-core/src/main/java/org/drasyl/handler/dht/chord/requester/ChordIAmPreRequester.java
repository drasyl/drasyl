package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordIAmPreRequester extends AbstractChordRequester<Notified, Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordIAmPreRequester.class);

    public ChordIAmPreRequester(final IdentityPublicKey peer,
                                final Promise<Void> promise) {
        super(IAmPre.of(), peer, promise);
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        LOG.info("Notify `{}` that we should be its predecessor.", peer);
        super.handlerAdded(ctx);
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final Notified response,
                                  final Promise<Void> promise) {
        promise.trySuccess(null);
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof Notified;
    }

    public static FutureComposer<Void> iAmPreRequest(final ChannelHandlerContext ctx,
                                                     final IdentityPublicKey peer) {
        final Promise<Void> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordIAmPreRequester(peer, promise));
        return composeFuture(ctx.executor(), promise);
    }
}
