/*
 * Copyright (c) 2020-2023 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.example;

import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.EventTypeDrasylNode;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeEvent;
import org.drasyl.node.event.PeerEvent;

@SuppressWarnings({ "InfiniteLoopStatement", "StatementWithEmptyBody" })
public class EventTypeDrasylNodeExample {
    public static void main(final String[] args) throws DrasylException {
        final DrasylNode node = new EventTypeDrasylNode() {
            @Override
            protected void onNodeEvent(final NodeEvent event) {
                System.out.println("node event = " + event);
            }

            @Override
            protected void onPeerEvent(final PeerEvent event) {
                System.out.println("peer event = " + event);
            }

            @Override
            protected void onMessage(final MessageEvent event) {
                System.out.println("message = " + event);
            }

            @Override
            protected void onAnyOtherEvent(final Event event) {
                System.out.println("other event = " + event);
            }
        };
        node.start();

        while (true) {
            // node should run forever
        }
    }
}
