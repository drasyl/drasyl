package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordClosestRequestHandler extends AbstractChordOneShotRequestHandler<MyClosest, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestRequestHandler.class);

    public ChordClosestRequestHandler(final IdentityPublicKey peer,
                                      final long findid,
                                      final Promise<IdentityPublicKey> promise) {
        super(Closest.of(findid), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MyClosest;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final MyClosest response,
                                  final Promise<IdentityPublicKey> promise) {
        promise.trySuccess(response.getAddress());
    }

    @Override
    protected Logger logger() {
        return LOG;
    }
}
