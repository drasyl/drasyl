package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.Alive;
import org.drasyl.handler.dht.chord.message.Keep;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordKeepRequestHandler extends AbstractChordOneShotRequestHandler<Alive, Void> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordKeepRequestHandler.class);

    public ChordKeepRequestHandler(final IdentityPublicKey peer,
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
}
