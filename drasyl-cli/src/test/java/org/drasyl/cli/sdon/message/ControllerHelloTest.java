/*
 * Copyright (c) 2020-2024 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.sdon.message;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import org.drasyl.cli.sdon.config.LinkPolicy;
import org.drasyl.cli.sdon.config.TunPolicy;
import org.drasyl.handler.codec.JacksonCodec;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static test.util.IdentityTestUtil.ID_1;

class ControllerHelloTest {
    @Nested
    class Serialization {
        @Test
        void shouldBeSerializable() throws UnknownHostException {
            final JacksonCodec<SdonMessage> handler = new JacksonCodec<>(SdonMessage.class);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);

            final ControllerHello msg = new ControllerHello(Set.of(
                    new TunPolicy(
                            InetAddress.getByName("127.0.0.1"),
                            (short) 8,
                            Map.of(
                                    InetAddress.getByName("127.0.0.1"),
                                    ID_1.getAddress()
                            )
                        ),
                    new LinkPolicy(
                            "n1",
                            ID_1.getAddress()
                    )
            ));
            channel.writeOutbound(msg);

            channel.checkException();

            final Object readMsg = channel.readOutbound();
            assertInstanceOf(ByteBuf.class, readMsg);

            ReferenceCountUtil.release(readMsg);
        }
    }
}
