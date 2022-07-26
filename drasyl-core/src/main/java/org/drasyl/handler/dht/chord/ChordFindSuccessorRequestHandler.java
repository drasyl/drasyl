package org.drasyl.handler.dht.chord;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

public class ChordFindSuccessorRequestHandler extends AbstractChordOneShotRequestHandler<FoundSuccessor, IdentityPublicKey> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorRequestHandler.class);

    public ChordFindSuccessorRequestHandler(final IdentityPublicKey peer,
                                            final long id,
                                            final Promise<IdentityPublicKey> promise) {
        super(FindSuccessor.of(id), peer, promise);
    }

    protected boolean acceptResponse(final Object msg) {
        return msg instanceof FoundSuccessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final FoundSuccessor response,
                                  final Promise<IdentityPublicKey> promise) {
        promise.trySuccess(response.getAddress());
    }

    @Override
    protected Logger logger() {
        return LOG;
    }
}
