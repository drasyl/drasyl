/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.plugin;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.identity.Identity;
import org.drasyl.pipeline.skeleton.HandlerAdapter;
import org.drasyl.pipeline.HandlerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

class PluginsIT {
    private final static Event event1 = mock(MessageEvent.class);
    private final static Event event2 = mock(MessageEvent.class);
    private PublishSubject<Event> receivedEvents;
    private DrasylConfig config;
    private DrasylNode node;

    @BeforeEach
    void setup() throws CryptoException, DrasylException {
        receivedEvents = PublishSubject.create();

        final Identity identity = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");

        config = DrasylConfig.newBuilder()
                .plugins(Set.of(new TestPlugin(ConfigFactory.empty())))
                .build();

        config = DrasylConfig.newBuilder(config)
                .identityProofOfWork(identity.getProofOfWork())
                .identityPublicKey(identity.getPublicKey())
                .identityPrivateKey(identity.getPrivateKey())
                .serverExposeEnabled(false)
                .superPeerEnabled(false)
                .build();

        node = new DrasylNode(config) {
            @Override
            public void onEvent(final Event event) {
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
            environment.getPipeline().addLast("TestHandler", new HandlerAdapter() {
                @Override
                public void handlerAdded(final HandlerContext ctx) {
                    ctx.fireEventTriggered(event1, new CompletableFuture<>());
                }
            });
            environment.getPipeline().processInbound(event2);
        }
    }
}