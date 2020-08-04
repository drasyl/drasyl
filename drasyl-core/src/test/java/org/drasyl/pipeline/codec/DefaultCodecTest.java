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
package org.drasyl.pipeline.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.JSONUtil;
import org.drasyl.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCodecTest {
    @Mock
    private HandlerContext ctx;
    @Mock
    private Identity identity;
    @Mock
    private CompressedPublicKey sender;
    @Mock
    private CompressedPublicKey recipient;
    @Mock
    private Consumer<Object> passOnConsumer;
    private DrasylConfig config;

    @BeforeEach
    void setUp() {
        config = DrasylConfig.newBuilder().build();
    }

    @Nested
    class Encode {
        @Test
        void shouldSkippByteArrays() {
            byte[] msg = new byte[]{};
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE);
            TestObserver<ApplicationMessage> testObserver = pipeline.outboundMessages().test();

            when(identity.getPublicKey()).thenReturn(sender);
            pipeline.processOutbound(recipient, msg);

            testObserver.awaitCount(1);
            testObserver.assertValue(new ApplicationMessage(sender, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, msg.getClass().getName()), msg));
        }

        @Test
        void passthroughsOnNotSerializiableMessages() {
            StringBuilder msg = new StringBuilder();

            when(ctx.validator()).thenReturn(TypeValidator.of(config));

            DefaultCodec.INSTANCE.encode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages2() {
            TypeValidator validator = TypeValidator.of(config);
            validator.addClass(InputStream.class);
            InputStream msg = mock(InputStream.class);

            when(ctx.validator()).thenReturn(TypeValidator.of(config));

            DefaultCodec.INSTANCE.encode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void shouldEncodePOJOs() throws JsonProcessingException {
            Integer msg = Integer.valueOf("10000");
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE);
            TestObserver<ApplicationMessage> testObserver = pipeline.outboundMessages().test();

            when(identity.getPublicKey()).thenReturn(sender);
            CompletableFuture<Void> future = pipeline.processOutbound(recipient, msg);

            testObserver.awaitCount(1);
            testObserver.assertValue(new ApplicationMessage(sender, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, Integer.class.getName()), JSONUtil.JACKSON_WRITER.writeValueAsBytes(msg)));
            future.join();
            assertTrue(future.isDone());
        }
    }

    @Nested
    class Decode {
        @Test
        void shouldSkippByteArrays() {
            ApplicationMessage msg = new ApplicationMessage(sender, recipient, new byte[]{});
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE);
            TestObserver<Pair<CompressedPublicKey, Object>> testObserver = pipeline.inboundMessages().test();

            pipeline.processInbound(msg);

            testObserver.awaitCount(1);
            testObserver.assertValue(Pair.of(sender, new byte[]{}));
        }

        @Test
        void passthroughsOnNotSerializiableMessages() {
            ObjectHolder msg = ObjectHolder.of(StringBuilder.class, new byte[]{
                    34, 34
            });

            when(ctx.validator()).thenReturn(TypeValidator.of(config));
            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages2() {
            TypeValidator validator = TypeValidator.of(config);
            validator.addClass(Vector.class);
            ObjectHolder msg = ObjectHolder.of(Vector.class, new byte[]{});

            when(ctx.validator()).thenReturn(validator);
            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void passthroughsOnNotSerializiableMessages3() {
            ObjectHolder msg = ObjectHolder.of("foo.bar.Class", new byte[]{});

            DefaultCodec.INSTANCE.decode(ctx, msg, passOnConsumer);

            verify(passOnConsumer).accept(msg);
        }

        @Test
        void shouldDecodePOJOs() throws JsonProcessingException {
            Integer integer = Integer.valueOf("10000");
            ApplicationMessage msg = new ApplicationMessage(sender, recipient, Map.of(ObjectHolder.CLASS_KEY_NAME, Integer.class.getName()), JSONUtil.JACKSON_WRITER.writeValueAsBytes(integer));
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE);
            TestObserver<Pair<CompressedPublicKey, Object>> testObserver = pipeline.inboundMessages().test();

            pipeline.processInbound(msg);

            testObserver.awaitCount(1);
            testObserver.assertValue(Pair.of(sender, integer));
        }
    }

    @Nested
    class Events {
        @Test
        void shouldPassEvents() {
            Event event = mock(Event.class);
            EmbeddedPipeline pipeline = new EmbeddedPipeline(identity, TypeValidator.of(config), ObjectHolder2ApplicationMessageHandler.INSTANCE, DefaultCodec.INSTANCE);
            TestObserver<Event> testObserver = pipeline.inboundEvents().test();

            pipeline.processInbound(event);

            testObserver.awaitCount(1);
            testObserver.assertValue(event);
        }
    }
}