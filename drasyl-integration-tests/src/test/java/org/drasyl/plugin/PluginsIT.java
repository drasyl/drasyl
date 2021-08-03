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
package org.drasyl.plugin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.annotation.NonNull;
import org.drasyl.channel.MigrationChannelHandler;
import org.drasyl.channel.MigrationEvent;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import test.util.IdentityTestUtil;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

@ExtendWith(MockitoExtension.class)
class PluginsIT {
    @Mock
    private static MessageEvent event1;
    @Mock
    private static MessageEvent event2;
    private PublishSubject<Event> receivedEvents;
    private DrasylConfig config;
    private DrasylNode node;

    @BeforeEach
    void setup() throws DrasylException {
        receivedEvents = PublishSubject.create();

        final Identity identity = IdentityTestUtil.ID_1;

        config = DrasylConfig.newBuilder()
                .plugins(Set.of(new TestPlugin(ConfigFactory.empty())))
                .build();

        config = DrasylConfig.newBuilder(config)
                .identityProofOfWork(identity.getProofOfWork())
                .identityPublicKey(identity.getIdentityPublicKey())
                .identitySecretKey(identity.getIdentitySecretKey())
                .remoteExposeEnabled(false)
                .remoteSuperPeerEnabled(false)
                .remoteBindPort(0)
                .build();

        node = new DrasylNode(config) {
            @Override
            public void onEvent(final @NonNull Event event) {
                receivedEvents.onNext(event);
            }
        };
    }

    @AfterEach
    void shutdown() {
        if (node != null) {
            node.shutdown();
        }
    }

    @Test
    void pluginShouldBeLoadedAndAlsoCorrespondingHandlers() {
        final TestObserver<Event> events = receivedEvents.filter(e -> e instanceof MessageEvent).test();

        node.start();

        events.awaitCount(2).assertValueCount(2);
        events.assertValues(event1, event2);
    }

    public static class TestPlugin implements DrasylPlugin {
        public TestPlugin(final Config config) {
            // do nothing
        }

        @Override
        public void onAfterStart(final PluginEnvironment environment) {
            environment.getPipeline().addFirst("TestHandler", new MigrationChannelHandler(new HandlerAdapter() {
                @Override
                public void onAdded(final HandlerContext ctx) {
                    final CompletableFuture<Void> future = new CompletableFuture<>();
                    ctx.passEvent(event1, future);
                }
            }));
            environment.getPipeline().fireUserEventTriggered(new MigrationEvent(event2, new CompletableFuture<>()));
        }
    }
}
