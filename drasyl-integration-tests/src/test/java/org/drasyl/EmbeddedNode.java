/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.ReplaySubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.annotation.NonNull;
import org.drasyl.event.Event;
import org.drasyl.event.InboundExceptionEvent;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.tcp.TcpServer;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.Closeable;
import java.util.Arrays;

import static java.util.Objects.requireNonNull;

public class EmbeddedNode extends DrasylNode implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedNode.class);
    private final Subject<Event> events;
    private int port;
    private int tcpFallbackPort;

    private EmbeddedNode(final DrasylConfig config,
                         final Subject<Event> events) throws DrasylException {
        super(config);
        this.events = requireNonNull(events);
    }

    public EmbeddedNode(final DrasylConfig config) throws DrasylException {
        this(config, ReplaySubject.<Event>create().toSerialized());
    }

    @Override
    public void onEvent(@NonNull final Event event) {
        if (event instanceof NodeUnrecoverableErrorEvent) {
            LOG.warn("{}", event);
            events.onError(((NodeUnrecoverableErrorEvent) event).getError());
        }
        else {
            if (event instanceof UdpServer.Port) {
                port = ((UdpServer.Port) event).getPort();
            }
            else if (event instanceof TcpServer.Port) {
                tcpFallbackPort = ((TcpServer.Port) event).getPort();
            }
            else if (event instanceof InboundExceptionEvent) {
                LOG.warn("{}", event, ((InboundExceptionEvent) event).getError());
            }

            events.onNext(event);

            if (event instanceof NodeNormalTerminationEvent) {
                events.onComplete();
            }
        }
    }

    @SuppressWarnings("unused")
    public Observable<Event> events() {
        return events;
    }

    @SuppressWarnings("unchecked")
    public <T extends Event> Observable<T> events(final Class<T> clazz) {
        return (Observable<T>) events.filter(clazz::isInstance);
    }

    @SuppressWarnings("unchecked")
    public Observable<Event> events(final Class<? extends Event>... clazzes) {
        return events.filter(event -> Arrays.stream(clazzes).anyMatch(clazz -> clazz.isInstance(event)));
    }

    public Observable<MessageEvent> messages() {
        return events(MessageEvent.class);
    }

    public EmbeddedNode started() {
        start();
        events(NodeUpEvent.class).test().awaitCount(1).assertValueCount(1);
        events(NodeUnrecoverableErrorEvent.class).test().assertNoValues();
        return this;
    }

    public EmbeddedNode online() {
        events(NodeOnlineEvent.class).test().awaitCount(1).assertValueCount(1);
        return this;
    }

    @Override
    public void close() {
        shutdown().join();
        // shutdown() future is completed before channelInactive has passed the pipeline...
        events(NodeNormalTerminationEvent.class).test().awaitCount(1).assertValueCount(1);
    }

    public int getPort() {
        events(UdpServer.Port.class).test().awaitCount(1).assertValueCount(1);
        return port;
    }

    public int getTcpFallbackPort() {
        if (tcpFallbackPort == 0) {
            throw new IllegalStateException("TCP Fallback Port not set. You have to start the node running in super peer mode with TCP fallback enabled first.");
        }
        return tcpFallbackPort;
    }
}
