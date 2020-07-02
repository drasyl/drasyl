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

import org.drasyl.event.Event;
import org.drasyl.peer.connection.message.ApplicationMessage;
import org.drasyl.util.CheckedConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private DrasylPipeline.HeadContext head;
    @Mock
    private DrasylPipeline.TailContext tail;
    @Mock
    private Consumer<Event> eventConsumer;
    @Mock
    private CheckedConsumer<ApplicationMessage> outboundConsumer;

    @BeforeEach
    void setUp() {
    }

    @Test
    void shouldAddHandlerOnFirstPosition() {
        when(head.getNext()).thenReturn(tail);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

        Handler handler = mock(Handler.class);

        pipeline.addFirst("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerOnLastPosition() {
        when(tail.getPrev()).thenReturn(head);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

        Handler handler = mock(Handler.class);

        pipeline.addLast("name", handler);

        verify(head).setNextHandlerContext(isA(AbstractHandlerContext.class));
        verify(tail).setPrevHandlerContext(isA(AbstractHandlerContext.class));
    }

    @Test
    void shouldAddHandlerBeforePosition() throws Exception {
        ArgumentCaptor<AbstractHandlerContext> captor = ArgumentCaptor.forClass(AbstractHandlerContext.class);
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

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
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

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
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

        assertThrows(NoSuchElementException.class, () -> pipeline.remove("name"));
    }

    @Test
    void shouldRemoveHandler() throws Exception {
        DrasylPipeline pipeline = new DrasylPipeline(handlerNames, head, tail, eventConsumer, outboundConsumer);

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
}