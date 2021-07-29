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
package org.drasyl.codec;

import org.drasyl.DrasylException;
import org.drasyl.event.Event;

import java.util.concurrent.ExecutionException;

public class NettyNodeExample {
    public static void main(final String[] args) throws DrasylException, InterruptedException, ExecutionException {
        final DrasylNode node = new DrasylNode() {
            @Override
            public void onEvent(final Event event) {
                System.out.println("NettyNodeExample.onEvent");
                System.out.println("event = " + event);
            }
        };

        node.start().join();

        Thread.sleep(5_000);

        System.out.println("send!");
        node.send("7b6f15b4c058c74708e8830ba0526156f3fa5d3f73cbae8331879644df83a0de", "Hallo").toCompletableFuture().get();

        //        node.shutdown().join();
    }
}
