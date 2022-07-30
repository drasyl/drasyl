package org.drasyl.handler.dht.chord.requester;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Promise;
import org.drasyl.handler.dht.chord.message.ChordMessage;
import org.drasyl.handler.dht.chord.message.MySuccessor;
import org.drasyl.handler.dht.chord.message.NothingSuccessor;
import org.drasyl.handler.dht.chord.message.YourSuccessor;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.util.FutureComposer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static org.drasyl.util.FutureComposer.composeFailedFuture;
import static org.drasyl.util.FutureComposer.composeFuture;

public class ChordYourSuccessorRequester extends AbstractChordRequester<ChordMessage, DrasylAddress> {
    private static final Logger LOG = LoggerFactory.getLogger(ChordYourSuccessorRequester.class);

    public ChordYourSuccessorRequester(final DrasylAddress peer,
                                       final Promise<DrasylAddress> promise) {
        super(YourSuccessor.of(), peer, promise);
    }

    @Override
    protected boolean acceptResponse(final Object msg) {
        return msg instanceof MySuccessor || msg instanceof NothingSuccessor;
    }

    @Override
    protected void handleResponse(final ChannelHandlerContext ctx,
                                  final ChordMessage response,
                                  final Promise<DrasylAddress> promise) {
        if (response instanceof MySuccessor) {
            promise.trySuccess(((MySuccessor) response).getAddress());
        }
        else {
            promise.trySuccess(null);
        }
    }

    @Override
    protected Logger logger() {
        return LOG;
    }

    public static FutureComposer<DrasylAddress> requestSuccessor(final ChannelHandlerContext ctx,
                                                                 final DrasylAddress peer) {
        if (peer == null) {
            return composeFailedFuture(new Exception("peer is null"));
        }
        final Promise<DrasylAddress> promise = ctx.executor().newPromise();
        ctx.pipeline().addBefore(ctx.name(), null, new ChordYourSuccessorRequester(peer, promise));
        return composeFuture().chain(promise);
    }
}
