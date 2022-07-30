package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.Closest;
import org.drasyl.handler.dht.chord.message.MyClosest;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordClosestRequester extends AbstractChordRequester<MyClosest, DrasylAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordClosestRequester.class);

    public ChordClosestRequester(final DrasylAddress peer,
                                 final long findid,
                                 final Promise<DrasylAddress> promise) {
        super(Closest.of(findid), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MyClosest;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final MyClosest response,
                                  final Promise<DrasylAddress> promise) {
        promise.trySuccess(response.getAddress());
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    public static FutureComposer<DrasylAddress> requestClosest(final ChannelHandlerContext ctx,
                                                               final DrasylAddress peer,
                                                               final long id) {
        if (peer == null) {
            return composeFailedFuture(new Exception("peer is null"));
        }
        final Promise<DrasylAddress> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordClosestRequester(peer, id, promise));
        return composeFuture().chain(promise);
    }
}
