package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.FindSuccessor;
import org.drasyl.handler.dht.chord.message.FoundSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordFindSuccessorRequester extends AbstractChordRequester<FoundSuccessor, DrasylAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordFindSuccessorRequester.class);

    public ChordFindSuccessorRequester(final DrasylAddress peer,
                                       final long id,
                                       final Promise<DrasylAddress> promise) {
        super(FindSuccessor.of(id), peer, promise);
    }

    protected boolean acceptResponse(final Object msg) {
        return msg instanceof FoundSuccessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final FoundSuccessor response,
                                  final Promise<DrasylAddress> promise) {
        promise.trySuccess(response.getAddress());
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    public static FutureComposer<DrasylAddress> findSuccessor(final ChannelHandlerContext ctx,
                                                              final long id,
                                                              final DrasylAddress peer) {
        if (peer == null) {
            return composeFailedFuture(new Exception("peer is null"));
        }
        final Promise<DrasylAddress> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordFindSuccessorRequester(peer, id, promise));
        return composeFuture().chain(promise);
    }
}
