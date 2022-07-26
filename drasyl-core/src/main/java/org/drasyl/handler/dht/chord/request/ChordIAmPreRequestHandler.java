package org.drasyl.handler.dht.chord.request;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.IAmPre;
import org.drasyl.handler.dht.chord.message.Notified;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordIAmPreRequestHandler extends AbstractChordOneShotRequestHandler<Notified, Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordIAmPreRequestHandler.class);

    public ChordIAmPreRequestHandler(final IdentityPublicKey peer,
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
}
