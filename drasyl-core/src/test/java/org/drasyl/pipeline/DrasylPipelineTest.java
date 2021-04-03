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
package org.drasyl.pipeline;

import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.remote.handler.UdpServer;
import org.drasyl.remote.handler.tcp.TcpClient;
import org.drasyl.remote.handler.tcp.TcpServer;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.drasyl.pipeline.DrasylPipeline.ARM_HANDLER;
import static org.drasyl.pipeline.DrasylPipeline.CHUNKING_HANDLER;
import static org.drasyl.pipeline.DrasylPipeline.HOP_COUNT_GUARD;
import static org.drasyl.pipeline.DrasylPipeline.INTERNET_DISCOVERY;
import static org.drasyl.pipeline.DrasylPipeline.INTRA_VM_DISCOVERY;
import static org.drasyl.pipeline.DrasylPipeline.INVALID_PROOF_OF_WORK_FILTER;
import static org.drasyl.pipeline.DrasylPipeline.LOCAL_HOST_DISCOVERY;
import static org.drasyl.pipeline.DrasylPipeline.LOOPBACK_MESSAGE_HANDLER;
import static org.drasyl.pipeline.DrasylPipeline.MESSAGE_SERIALIZER;
import static org.drasyl.pipeline.DrasylPipeline.MONITORING_HANDLER;
import static org.drasyl.pipeline.DrasylPipeline.OTHER_NETWORK_FILTER;
import static org.drasyl.pipeline.DrasylPipeline.PORT_MAPPER;
import static org.drasyl.pipeline.DrasylPipeline.REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC;
import static org.drasyl.pipeline.DrasylPipeline.STATIC_ROUTES_HANDLER;
import static org.drasyl.pipeline.DrasylPipeline.TCP_CLIENT;
import static org.drasyl.pipeline.DrasylPipeline.UDP_SERVER;
import static org.drasyl.pipeline.HeadContext.DRASYL_HEAD_HANDLER;
import static org.drasyl.pipeline.TailContext.DRASYL_TAIL_HANDLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylPipelineTest {
    @Mock
    private Map<String, AbstractHandlerContext> handlerNames;
    @Mock
    private AbstractEndHandler head;
    @Mock
    private AbstractEndHandler tail;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private DrasylScheduler scheduler;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Supplier<UdpServer> udpServerSupplier;
    @Mock
    private Semaphore outboundMessagesBuffer;
    @Mock
    private Supplier<TcpServer> tcpServerSupplier;
    @Mock
    private Supplier<TcpClient> tcpClientSupplier;

    @Nested
    class Constructor {
        @Test
        void shouldCreateNewPipeline(@Mock final UdpServer udpServer,
                                     @Mock final TcpClient tcpClient) {
            when(config.isIntraVmDiscoveryEnabled()).thenReturn(true);
            when(config.isRemoteEnabled()).thenReturn(true);
            when(config.getRemoteStaticRoutes().isEmpty()).thenReturn(false);
            when(config.isRemoteLocalHostDiscoveryEnabled()).thenReturn(true);
            when(config.isMonitoringEnabled()).thenReturn(true);
            when(config.isRemoteMessageArmEnabled()).thenReturn(true);
            when(config.isRemoteExposeEnabled()).thenReturn(true);
            when(config.isRemoteTcpFallbackEnabled()).thenReturn(true);
            when(config.isRemoteSuperPeerEnabled()).thenReturn(true);
            when(udpServerSupplier.get()).thenReturn(udpServer);
            when(tcpClientSupplier.get()).thenReturn(tcpClient);

            final Pipeline pipeline = new DrasylPipeline(eventConsumer, config, identity, peersManager, udpServerSupplier, tcpServerSupplier, tcpClientSupplier);

            // Test if head and tail handlers are added
            assertNull(pipeline.get(DRASYL_HEAD_HANDLER));
            assertNull(pipeline.context(DRASYL_HEAD_HANDLER));
            assertNull(pipeline.get(DRASYL_TAIL_HANDLER));
            assertNull(pipeline.context(DRASYL_TAIL_HANDLER));

            // Test if default handler are added
            assertNotNull(pipeline.get(LOOPBACK_MESSAGE_HANDLER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(INTRA_VM_DISCOVERY), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(MESSAGE_SERIALIZER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(STATIC_ROUTES_HANDLER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(LOCAL_HOST_DISCOVERY), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(INTERNET_DISCOVERY), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(HOP_COUNT_GUARD), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(MONITORING_HANDLER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(ARM_HANDLER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(INVALID_PROOF_OF_WORK_FILTER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(OTHER_NETWORK_FILTER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(CHUNKING_HANDLER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(REMOTE_ENVELOPE_TO_BYTE_BUF_CODEC), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(TCP_CLIENT), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(PORT_MAPPER), "This handler is required in the DrasylPipeline");
            assertNotNull(pipeline.get(UDP_SERVER), "This handler is required in the DrasylPipeline");
        }
    }

    @Nested
    class AddFirst {
        @Test
        void shouldAddHandlerOnFirstPosition(@Mock final Handler handler) {
            when(head.getNext()).thenReturn(tail);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            pipeline.addFirst("name", handler);

            verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
            verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
        }
    }

    @Nested
    class AddLast {
        @Test
        void shouldAddHandlerOnLastPosition(@Mock final Handler handler) {
            when(tail.getPrev()).thenReturn(head);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            pipeline.addLast("name", handler);

            verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
            verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
        }
    }

    @Nested
    class AddBefore {
        @Test
        void shouldAddHandlerBeforePosition(@Mock final Handler handler,
                                            @Mock final AbstractHandlerContext baseCtx) {
            final ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.get("name1")).thenReturn(baseCtx);
            when(baseCtx.getPrev()).thenReturn(head);
            pipeline.addBefore("name1", "name2", handler);

            verify(baseCtx).setPrevHandlerContext(captor.capture());
            verify(baseCtx, never()).setNextHandlerContext(any());

            assertSame(handler, captor.getValue().handler());
            assertSame(captor.getValue().getPrev(), head);
            assertSame(captor.getValue().getNext(), baseCtx);

            verify(head).setNextHandlerContext(same(captor.getValue()));
            verify(head, never()).setPrevHandlerContext(any());

            verify(captor.getValue().handler()).onAdded(same(captor.getValue()));
        }
    }

    @Nested
    class AddAfter {
        @Test
        void shouldAddHandlerAfterPosition(@Mock final Handler handler,
                                           @Mock final AbstractHandlerContext baseCtx) {
            final ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.get("name1")).thenReturn(baseCtx);
            when(baseCtx.getNext()).thenReturn(tail);
            pipeline.addAfter("name1", "name2", handler);

            verify(baseCtx).setNextHandlerContext(captor.capture());
            verify(tail).setPrevHandlerContext(captor.getValue());
            assertEquals(handler, captor.getValue().handler());
            verify(captor.getValue().handler()).onAdded(captor.getValue());
        }
    }

    @Nested
    class Remove {
        @Test
        void shouldRemoveHandler(@Mock final AbstractHandlerContext ctx,
                                 @Mock final Handler handler) {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.remove("name")).thenReturn(ctx);
            when(ctx.handler()).thenReturn(handler);
            when(ctx.getPrev()).thenReturn(head);
            when(ctx.getNext()).thenReturn(tail);
            pipeline.remove("name");

            verify(head).setNextHandlerContext(tail);
            verify(tail).setPrevHandlerContext(head);
            verify(handler).onRemoved(ctx);
        }

        @Test
        void shouldThrowExceptionIfHandlerDoesNotExists() {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            assertThrows(NoSuchElementException.class, () -> pipeline.remove("name"));
        }
    }

    @Nested
    class Replace {
        @Test
        void shouldReplaceHandler(@Mock final Handler oldHandler,
                                  @Mock final Handler newHandler,
                                  @Mock final AbstractHandlerContext oldCtx) {
            final ArgumentCaptor<AbstractHandlerContext> captor1 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
            final ArgumentCaptor<AbstractHandlerContext> captor2 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.remove("oldName")).thenReturn(oldCtx);
            when(oldCtx.handler()).thenReturn(oldHandler);
            when(oldCtx.getPrev()).thenReturn(head);
            when(oldCtx.getNext()).thenReturn(tail);

            pipeline.replace("oldName", "newName", newHandler);

            verify(oldHandler).onRemoved(oldCtx);
            verify(head).setNextHandlerContext(captor1.capture());
            verify(tail).setPrevHandlerContext(captor2.capture());

            assertEquals(captor1.getValue(), captor2.getValue());
            assertEquals(newHandler, captor1.getValue().handler());

            verify(newHandler).onAdded(captor1.getValue());
        }
    }

    @Nested
    class Get {
        @Test
        void shouldReturnCorrectHandler(@Mock final AbstractHandlerContext ctx,
                                        @Mock final Handler handler) {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.containsKey("name")).thenReturn(true);
            when(handlerNames.get("name")).thenReturn(ctx);
            when(ctx.handler()).thenReturn(handler);

            assertEquals(handler, pipeline.get("name"));
        }
    }

    @Nested
    class Context {
        @Test
        void shouldReturnCorrectContext(@Mock final AbstractHandlerContext ctx) {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            when(handlerNames.get("name")).thenReturn(ctx);

            assertEquals(ctx, pipeline.context("name"));
        }
    }

    @Nested
    class ProcessInboundMessage {
        @SuppressWarnings("rawtypes")
        @Test
        void shouldProcessMessage(@Mock final CompressedPublicKey sender,
                                  @Mock final RemoteEnvelope msg) {
            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            final CompletableFuture<Void> future = pipeline.processInbound(sender, msg);

            verify(scheduler).scheduleDirect(captor.capture());
            captor.getValue().run();
            verify(head).passInbound(sender, msg, future);
        }
    }

    @Nested
    class ProcessInboundEvent {
        @Test
        void shouldProcessEvent(@Mock final Event event) {
            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            final CompletableFuture<Void> future = pipeline.processInbound(event);

            verify(scheduler).scheduleDirect(captor.capture());
            captor.getValue().run();
            verify(head).passEvent(event, future);
        }
    }

    @Nested
    class ProcessOutboundMessage {
        @Test
        void shouldProcessMessage(@Mock final CompressedPublicKey recipient,
                                  @Mock final AddressedEnvelope<?, ?> msg) {
            when(outboundMessagesBuffer.tryAcquire()).thenReturn(true);

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            final CompletableFuture<Void> future = pipeline.processOutbound(recipient, msg);

            verify(scheduler).scheduleDirect(captor.capture());
            captor.getValue().run();
            verify(tail).passOutbound(recipient, msg, future);
            verify(outboundMessagesBuffer).tryAcquire();
        }

        @Test
        void shouldNotProcessMessageIfBufferIsFull(@Mock final CompressedPublicKey recipient,
                                                   @Mock final AddressedEnvelope<?, ?> msg) {
            when(outboundMessagesBuffer.tryAcquire()).thenReturn(false);

            final ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            assertThrows(CompletionException.class, pipeline.processOutbound(recipient, msg)::join);
        }
    }

    @Nested
    class IsWritable {
        @Test
        void shouldReturnTrueIfNoBufferExists() {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, null);

            assertTrue(pipeline.isWritable());
        }

        @Test
        void shouldReturnTrueIfBufferIsNotFull() {
            when(outboundMessagesBuffer.availablePermits()).thenReturn(1);

            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            assertTrue(pipeline.isWritable());
        }
    }

    @Nested
    class MessagesBeforeUnwritable {
        @Test
        void shouldReturnMaxIntegerValueIfNoBufferExists() {
            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, null);

            assertEquals(Integer.MAX_VALUE, pipeline.messagesBeforeUnwritable());
        }

        @Test
        void shouldReturnNumberOfMessagesThatStillFitIntoTheBuffer() {
            when(outboundMessagesBuffer.availablePermits()).thenReturn(1337);

            final Pipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity, outboundMessagesBuffer);

            assertEquals(1337, pipeline.messagesBeforeUnwritable());
        }
    }
}
