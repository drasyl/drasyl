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
package org.drasyl.pipeline;

import io.reactivex.rxjava3.core.Scheduler;
import org.drasyl.DrasylConfig;
import org.drasyl.event.Event;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.pipeline.codec.ObjectHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.drasyl.pipeline.HeadContext.DRASYL_HEAD_HANDLER;
import static org.drasyl.pipeline.TailContext.DRASYL_TAIL_HANDLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

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
    private Scheduler scheduler;
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;

    @Test
    void shouldCreateNewPipeline() {
        DrasylPipeline pipeline = new DrasylPipeline(eventConsumer, config, identity);

        assertNull(pipeline.get(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.context(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.get(DRASYL_TAIL_HANDLER));
        assertNull(pipeline.context(DRASYL_TAIL_HANDLER));
    }

    @Test
    void shouldAddHandlerOnFirstPosition() {
        when(head.getNext()).thenReturn(tail);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        Handler handler = mock(Handler.class);

        pipeline.addFirst("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerOnLastPosition() {
        when(tail.getPrev()).thenReturn(head);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        Handler handler = mock(Handler.class);

        pipeline.addLast("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerBeforePosition() {
        ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext baseCtx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

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

        verify(captor.getValue().handler()).handlerAdded(same(captor.getValue()));
    }

    @Test
    void shouldAddHandlerAfterPosition() {
        ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext baseCtx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.get("name1")).thenReturn(baseCtx);
        when(baseCtx.getNext()).thenReturn(tail);
        pipeline.addAfter("name1", "name2", handler);

        verify(baseCtx).setNextHandlerContext(captor.capture());
        verify(tail).setPrevHandlerContext(eq(captor.getValue()));
        assertEquals(handler, captor.getValue().handler());
        verify(captor.getValue().handler()).handlerAdded(eq(captor.getValue()));
    }

    @Test
    void shouldThrowExceptionIfHandlerDoesNotExistsOnRemoveHandler() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        assertThrows(NoSuchElementException.class, () -> pipeline.remove("name"));
    }

    @Test
    void shouldRemoveHandler() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.remove("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);
        when(ctx.getPrev()).thenReturn(head);
        when(ctx.getNext()).thenReturn(tail);
        pipeline.remove("name");

        verify(head).setNextHandlerContext(tail);
        verify(tail).setPrevHandlerContext(head);
        verify(handler).handlerRemoved(ctx);
    }

    @Test
    void shouldReplaceHandler() {
        ArgumentCaptor<AbstractHandlerContext> captor1 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        ArgumentCaptor<AbstractHandlerContext> captor2 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext oldCtx = mock(AbstractHandlerContext.class);
        Handler oldHandler = mock(Handler.class);

        when(handlerNames.remove("oldName")).thenReturn(oldCtx);
        when(oldCtx.handler()).thenReturn(oldHandler);
        when(oldCtx.getPrev()).thenReturn(head);
        when(oldCtx.getNext()).thenReturn(tail);

        Handler newHandler = mock(Handler.class);

        pipeline.replace("oldName", "newName", newHandler);

        verify(oldHandler).handlerRemoved(oldCtx);
        verify(head).setNextHandlerContext(captor1.capture());
        verify(tail).setPrevHandlerContext(captor2.capture());

        assertEquals(captor1.getValue(), captor2.getValue());
        assertEquals(newHandler, captor1.getValue().handler());

        verify(newHandler).handlerAdded(captor1.getValue());
    }

    @Test
    void shouldReturnCorrectHandler() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.containsKey("name")).thenReturn(true);
        when(handlerNames.get("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);

        assertEquals(handler, pipeline.get("name"));
    }

    @Test
    void shouldReturnCorrectContext() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);

        when(handlerNames.get("name")).thenReturn(ctx);

        assertEquals(ctx, pipeline.context("name"));
    }

    @Test
    void shouldExecuteInboundMessage() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        byte[] payload = new byte[]{};
        CompressedPublicKey sender = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);
        when(msg.getSender()).thenReturn(sender);
        when(msg.getPayload()).thenReturn(payload);
        doReturn(payload.getClass().getName()).when(msg).getHeader(ObjectHolder.CLASS_KEY_NAME);

        CompletableFuture<Void> future = pipeline.processInbound(msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireRead(eq(sender), eq(ObjectHolder.of(payload.getClass(), payload)), eq(future));
    }

    @Test
    void shouldExecuteInboundEvent() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        Event event = mock(Event.class);

        CompletableFuture<Void> future = pipeline.processInbound(event);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireEventTriggered(eq(event), eq(future));
    }

    @Test
    void shouldExecuteOutboundMessage() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler, config, identity);

        CompressedPublicKey recipient = mock(CompressedPublicKey.class);
        ApplicationMessage msg = mock(ApplicationMessage.class);

        CompletableFuture<Void> future = pipeline.processOutbound(recipient, msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(tail).write(eq(recipient), eq(msg), eq(future));
    }
}