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
package org.drasyl.peer.connection.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.connection.message.Message;
import org.drasyl.peer.connection.message.MessageId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

class MessageEncoderTest {
    private static CompressedPublicKey sender;
    private static CompressedPublicKey recipient;
    private Message message;
    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() throws CryptoException {
        message = new MyMessage();

        final ChannelHandler handler = MessageEncoder.INSTANCE;
        channel = new EmbeddedChannel(handler);
        sender = CompressedPublicKey.of("030507fa840cc2f6706f285f5c6c055f0b7b3efb85885227cb306f176209ff6fc3");
        recipient = CompressedPublicKey.of("0229041b273dd5ee1c2bef2d77ae17dbd00d2f0a2e939e22d42ef1c4bf05147ea9");
    }

    @Test
    void shouldSerializeOutboundMessageToJsonString() {
        channel.writeOutbound(message);
        channel.flush();

        final BinaryWebSocketFrame frame = channel.readOutbound();

        final byte[] actual = new byte[frame.content().readableBytes()];
        frame.content().readBytes(actual);

        assertThatJson(new String(actual))
                .isObject()
                .containsEntry("@type", "MessageEncoderTest$MyMessage")
                .containsKeys("id", "proofOfWork", "sender", "userAgent");

        // Important: release the ByteBuf after testing, otherwise the ResourceLeakDetector raises alarms for tests
        frame.release();
    }

    private static class MyMessage implements Message {
        @Override
        public MessageId getId() {
            return MessageId.of("d8bc65cf7dc1951e96313055");
        }

        @Override
        public String getUserAgent() {
            return "";
        }

        @Override
        public int getNetworkId() {
            return 1;
        }

        @Override
        public CompressedPublicKey getSender() {
            return sender;
        }

        @Override
        public ProofOfWork getProofOfWork() {
            return ProofOfWork.of(6657650);
        }

        @Override
        public CompressedPublicKey getRecipient() {
            return recipient;
        }

        @Override
        public short getHopCount() {
            return 0;
        }

        @Override
        public void incrementHopCount() {
            // do nothing
        }
    }
}