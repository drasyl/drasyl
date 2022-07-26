package org.drasyl.handler.dht.chord.request;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.MyPredecessor;
import org.drasyl.handler.dht.chord.message.NothingPredecessor;
import org.drasyl.handler.dht.chord.message.YourPredecessor;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordYourPredecessorRequestHandler extends AbstractChordOneShotRequestHandler<ChordMessage, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordYourPredecessorRequestHandler.class);

    public ChordYourPredecessorRequestHandler(final IdentityPublicKey peer,
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
}
