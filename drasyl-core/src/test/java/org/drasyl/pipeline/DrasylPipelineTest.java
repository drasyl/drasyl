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
import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.CheckedConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static org.drasyl.pipeline.HeadContext.DRASYL_HEAD_HANDLER;
import static org.drasyl.pipeline.TailContext.DRASYL_TAIL_HANDLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DrasylPipelineTest {
    @Mock
    private Map<String, AbstractHandlerContext> handlerNames;
    @Mock
    private AbstractHandlerContext head;
    @Mock
    private AbstractHandlerContext tail;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private CheckedConsumer<ApplicationMessage> outboundConsumer;
    @Mock
    private Scheduler scheduler;

    @Test
    void shouldCreateNewPipeline() {
        DrasylPipeline pipeline = new DrasylPipeline(eventConsumer, outboundConsumer);

        assertNull(pipeline.get(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.context(DRASYL_HEAD_HANDLER));
        assertNull(pipeline.get(DRASYL_TAIL_HANDLER));
        assertNull(pipeline.context(DRASYL_TAIL_HANDLER));
    }

    @Test
    void shouldAddHandlerOnFirstPosition() {
        when(head.getNext()).thenReturn(tail);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        Handler handler = mock(Handler.class);

        pipeline.addFirst("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerOnLastPosition() {
        when(tail.getPrev()).thenReturn(head);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        Handler handler = mock(Handler.class);

        pipeline.addLast("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerBeforePosition() throws Exception {
        ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        AbstractHandlerContext baseCtx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.get("name1")).thenReturn(baseCtx);
        when(baseCtx.getPrev()).thenReturn(head);
        pipeline.addBefore("name1", "name2", handler);

        verify(baseCtx).setPrevHandlerContext(captor.capture());
        verify(head).setNextHandlerContext(eq(captor.getValue()));
        assertEquals(handler, captor.getValue().handler());
        verify(captor.getValue().handler()).handlerAdded(eq(captor.getValue()));
    }

    @Test
    void shouldAddHandlerAfterPosition() throws Exception {
        ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

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
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        assertThrows(NoSuchElementException.class, () -> pipeline.remove("name"));
    }

    @Test
    void shouldRemoveHandler() throws Exception {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.remove("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);
        when(ctx.getPrev()).thenReturn(head);
        when(ctx.getNext()).thenReturn(tail);
        pipeline.remove("name");

        verify(head).setNextHandlerContext(tail);
        verify(tail).setPrevHandlerContext(head);
        verify(ctx).setNextHandlerContext(null);
        verify(ctx).setPrevHandlerContext(null);
        verify(handler).handlerRemoved(ctx);
    }

    @Test
    void shouldReplaceHandler() throws Exception {
        ArgumentCaptor<AbstractHandlerContext> captor1 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        ArgumentCaptor<AbstractHandlerContext> captor2 = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        AbstractHandlerContext oldCtx = mock(AbstractHandlerContext.class);
        Handler oldHandler = mock(Handler.class);

        when(handlerNames.remove("oldName")).thenReturn(oldCtx);
        when(oldCtx.handler()).thenReturn(oldHandler);
        when(oldCtx.getPrev()).thenReturn(head);
        when(oldCtx.getNext()).thenReturn(tail);

        Handler newHandler = mock(Handler.class);

        pipeline.replace("oldName", "newName", newHandler);

        verify(oldHandler).handlerRemoved(oldCtx);
        verify(oldCtx).setPrevHandlerContext(null);
        verify(oldCtx).setNextHandlerContext(null);
        verify(head).setNextHandlerContext(captor1.capture());
        verify(tail).setPrevHandlerContext(captor2.capture());

        assertEquals(captor1.getValue(), captor2.getValue());
        assertEquals(newHandler, captor1.getValue().handler());

        verify(newHandler).handlerAdded(captor1.getValue());
    }

    @Test
    void shouldReturnCorrectHandler() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);
        Handler handler = mock(Handler.class);

        when(handlerNames.containsKey("name")).thenReturn(true);
        when(handlerNames.get("name")).thenReturn(ctx);
        when(ctx.handler()).thenReturn(handler);

        assertEquals(handler, pipeline.get("name"));
    }

    @Test
    void shouldReturnCorrectContext() {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        AbstractHandlerContext ctx = mock(AbstractHandlerContext.class);

        when(handlerNames.get("name")).thenReturn(ctx);

        assertEquals(ctx, pipeline.context("name"));
    }

    @Test
    void shouldExecuteInboundMessage() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        ApplicationMessage msg = mock(ApplicationMessage.class);

        pipeline.executeInbound(msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireRead(eq(msg));
    }

    @Test
    void shouldExecuteInboundEvent() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        Event event = mock(Event.class);

        pipeline.executeInbound(event);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(head).fireEventTriggered(eq(event));
    }

    @Test
    void shouldExecuteOutboundMessage() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, scheduler);

        ApplicationMessage msg = mock(ApplicationMessage.class);

        pipeline.executeOutbound(msg);

        verify(scheduler).scheduleDirect(captor.capture());
        captor.getValue().run();
        verify(tail).write(eq(msg));
    }
}