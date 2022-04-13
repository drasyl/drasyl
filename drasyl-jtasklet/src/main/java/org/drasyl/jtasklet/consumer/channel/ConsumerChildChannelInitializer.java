package org.drasyl.jtasklet.consumer.channel;

import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.cli.handler.SpawnChildChannelToPeer;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.channel.AbstractChildChannelInitializer;
import org.drasyl.jtasklet.consumer.handler.OffloadTaskHandler;
import org.drasyl.jtasklet.consumer.handler.ResourceRequestHandler;
import org.drasyl.util.Worm;

import java.io.PrintStream;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

public class ConsumerChildChannelInitializer extends AbstractChildChannelInitializer {
    private final PrintStream err;
    private final Worm<Integer> exitCode;
    private final IdentityPublicKey broker;
    private final String source;
    private final Object[] input;
    private final AtomicReference<IdentityPublicKey> provider;
    private final Consumer<Object[]> outputConsumer;
    private final AtomicReference<Instant> requestResourceTime;
    private final AtomicReference<Instant> resourceResponseTime;
    private final AtomicReference<Instant> offloadTaskTime;
    private final AtomicReference<Instant> returnResultTime;
    private final AtomicReference<String> token;

    @SuppressWarnings("java:S107")
    public ConsumerChildChannelInitializer(final PrintStream out,
                                           final PrintStream err,
                                           final Worm<Integer> exitCode,
                                           final IdentityPublicKey broker,
                                           final String source,
                                           final Object[] input,
                                           final AtomicReference<IdentityPublicKey> provider,
                                           final Consumer<Object[]> outputConsumer,
                                           final AtomicReference<Instant> requestResourceTime,
                                           final AtomicReference<Instant> resourceResponseTime,
                                           final AtomicReference<Instant> offloadTaskTime,
                                           final AtomicReference<Instant> returnResultTime,
                                           final AtomicReference<String> token) {
        super(out);
        this.err = requireNonNull(err);
        this.exitCode = requireNonNull(exitCode);
        this.broker = requireNonNull(broker);
        this.source = requireNonNull(source);
        this.input = requireNonNull(input);
        this.provider = requireNonNull(provider);
        this.outputConsumer = requireNonNull(outputConsumer);
        this.requestResourceTime = requireNonNull(requestResourceTime);
        this.resourceResponseTime = requireNonNull(resourceResponseTime);
        this.offloadTaskTime = requireNonNull(offloadTaskTime);
        this.returnResultTime = requireNonNull(returnResultTime);
        this.token = requireNonNull(token);
    }

    @Override
    protected void lastStage(DrasylChannel ch) {
        final boolean isBroker = ch.remoteAddress().equals(broker);
        final boolean isProvider = ch.remoteAddress().equals(provider.get());

        // consumer
        if (isBroker) {
            brokerStage(ch);
        }
        else if (isProvider) {
            providerStage(ch);
        }

        super.lastStage(ch);
    }

    private void brokerStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new ResourceRequestHandler(out, provider, requestResourceTime, resourceResponseTime, token));

        // always create a new channel to the broker
        ch.closeFuture().addListener(future -> ch.parent().pipeline().addFirst(new SpawnChildChannelToPeer((DrasylServerChannel) ch.parent(), (IdentityPublicKey) ch.remoteAddress())));
    }

    private void providerStage(final DrasylChannel ch) {
        ch.pipeline().addLast(new OffloadTaskHandler(out, source, input, outputConsumer, offloadTaskTime, returnResultTime, token));
    }
}
