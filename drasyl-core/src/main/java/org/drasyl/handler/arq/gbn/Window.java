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
package org.drasyl.handler.arq.gbn;

import io.netty.channel.ChannelPromise;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * This class does model a sliding window in the Go-Back-N ARQ protocol.
 */
public interface Window {
    /**
     * Adds the given msg to the window, if there is enough capacity.
     *
     * @param msg     the message to add
     * @param promise the write promise
     * @return {@code true}, if the message could be added, {@code false} if the window has no
     * capacity
     */
    boolean add(GoBackNArqData msg, ChannelPromise promise);

    /**
     * Removes the oldest message from the window.
     *
     * @return the write promise of the oldest message
     */
    ChannelPromise remove();

    /**
     * @return the original unacknowledged message queue
     */
    List<Frame> getQueue();

    /**
     * @return the size of the window
     */
    int size();

    /**
     * @return the free space of the window
     */
    int getFreeSpace();

    /**
     * Clears the window and let's fail all write promises.
     *
     * @param cause the cause of the failure
     */
    void removeAndFailAll(Throwable cause);

    final class Frame {
        private final GoBackNArqData msg;
        private final ChannelPromise promise;

        Frame(final GoBackNArqData msg, final ChannelPromise promise) {
            this.msg = requireNonNull(msg);
            this.promise = requireNonNull(promise);
        }

        public GoBackNArqData getMsg() {
            return msg;
        }

        public ChannelPromise getPromise() {
            return promise;
        }
    }
}
