/*
 * Copyright (c) 2020
 *
 * This file is part of Relayserver.
 *
 * Relayserver is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Relayserver is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Relayserver.  If not, see <http://www.gnu.org/licenses/>.
 */

package city.sane.relay.server.testutils;

import city.sane.relay.common.messages.ForwardableMessage;
import city.sane.relay.common.messages.UserAgentMessage;
import city.sane.relay.common.models.SessionChannel;
import city.sane.relay.common.models.SessionUID;
import city.sane.relay.server.RelayServer;
import city.sane.relay.server.RelayServerConfig;
import city.sane.relay.server.RelayServerException;
import com.typesafe.config.ConfigFactory;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

/**
 * This class starts a new test Client to debug/develop the monitoring UI.
 */
public class TestSetupForUI {
    public static final int ADDITIONAL_SYSTEMS = 10;

    public static void main(String[] args) throws RelayServerException, URISyntaxException {
        Timer timer = new Timer(true);
        UserAgentMessage.userAgentGenerator = () -> {
            return UserAgentMessage.defaultUserAgentGenerator.get() + " TestClientForUI";
        };

        RelayServerConfig conf1 = new RelayServerConfig(ConfigFactory.load("configs/UITest/relay1.conf"));
//        RelayServerConfig conf2 = new RelayServerConfig(ConfigFactory.load("configs/UITest/relay2.conf"));
        RelayServer server1 = new RelayServer(conf1);
//        RelayServer server2 = new RelayServer(conf2);

        TestHelper.giveRelayServerEnv(() -> {
//            TestHelper.giveRelayServerEnv(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(3000); // NOSONAR
                    TestSession testSession = TestSession.build(server1, SessionUID.of("clientUID1"),
                            Set.of(SessionChannel.of("testChannel")));
//                        TestSession.build(server1, SessionUID.of("clientUID4"),
//                                Set.of(SessionChannel.of("testChannel")));
                    timer.scheduleAtFixedRate(wrap(() ->
                            testSession.sendMessage(new ForwardableMessage(testSession.getUID(), SessionUID.ALL,
                                    "Hello World!".getBytes(StandardCharsets.UTF_8)))
                    ), 60000L, 1000L);


                    for (int i = 0; i < ADDITIONAL_SYSTEMS; i++) {
//                            RelayServer server = List.of(server1, server2).get(RandomUtil.randomNumber(2));
                        try {
                            TestSession session = TestSession.build(server1, Set.of(SessionChannel.of("testChannel")));

                            session.addListener(message -> {
                                if (message instanceof ForwardableMessage) {
                                    // Simple Echo
                                    ForwardableMessage msg = (ForwardableMessage) message;
                                    session.sendMessage(new ForwardableMessage(msg.getReceiverUID(), SessionUID.ALL
                                            , msg.getBlob()));
                                }
                            });
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }).start();
//            }, conf2, server2, false);
        }, conf1, server1, false);

    }

    private static TimerTask wrap(Runnable r) {
        return new TimerTask() {
            @Override
            public void run() {
                r.run();
            }
        };
    }

}
