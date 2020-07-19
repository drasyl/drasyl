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
package org.drasyl.plugins;

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
import org.drasyl.pipeline.Handler;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.InboundHandlerAdapter;
import org.drasyl.pipeline.Pipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        Identity identity = Identity.of(169092, "030a59784f88c74dcd64258387f9126739c3aeb7965f36bb501ff01f5036b3d72b", "0f1e188d5e3b98daf2266d7916d2e1179ae6209faa7477a2a66d4bb61dab4399");

        Config pluginConfig = ConfigFactory.parseString("drasyl.plugins {\n" +
                " test-plugin {\n" +
                "  class = \"org.drasyl.plugins.PluginsIT$TestPlugin\"\n" +
                "  enabled = true\n" +
                "  options {}\n" +
                " }\n" +
                "}");

        config = new DrasylConfig(ConfigFactory.load().withFallback(pluginConfig));

        config = DrasylConfig.newBuilder(config)
                .identityProofOfWork(identity.getProofOfWork())
                .identityPublicKey(identity.getPublicKey())
                .identityPrivateKey(identity.getPrivateKey())
                .build();

        node = new DrasylNode(config) {
            @Override
            public void onEvent(Event event) {
                receivedEvents.onNext(event);
            }
        };
    }

    @AfterEach
    void shutdown() {
        node.shutdown();
    }

    @Test
    void pluginShouldBeLoadedAndAlsoCorrespondingHandlers() {
        TestObserver<Event> events = receivedEvents.filter(e -> e instanceof MessageEvent).test();

        node.start();

        events.awaitCount(2);
        events.assertValues(event1, event2);
    }

    public static class TestPlugin extends AutoloadablePlugin {
        private final Handler testHandler;

        public TestPlugin(Pipeline pipeline,
                          DrasylConfig config,
                          PluginEnvironment environment) {
            super(pipeline, config, environment);

            this.testHandler = new InboundHandlerAdapter() {
                @Override
                public void handlerAdded(HandlerContext ctx) {
                    ctx.fireEventTriggered(event1);
                }
            };
        }

        @Override
        public List<Handler> getHandler() {
            return List.of(testHandler);
        }

        @Override
        public String name() {
            return "PluginsIT.TestPlugin";
        }

        @Override
        public String description() {
            return "This is a test plugin.";
        }

        @Override
        public void onRemove() {
            // Do nothing
        }

        @Override
        public void onAdded() {
            pipeline.processInbound(event2);
        }
    }
}
