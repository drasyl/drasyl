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
package org.drasyl.cli.command.wormhole;

import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.DrasylNodeChannelInitializer;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.JacksonCodec;
import org.drasyl.channel.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.channel.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.channel.arq.stopandwait.StopAndWaitArqHandler;

class WormholeChannelInitializer extends DrasylNodeChannelInitializer {
    public static final int ARQ_RETRY_TIMEOUT_MILLIS = 250;

    public WormholeChannelInitializer(final DrasylConfig config, final DrasylNode node) {
        super(config, node);
    }

    @Override
    protected void serializationStage(final DrasylChannel ch) {
        // (de)serializer for WormholeMessages
        ch.pipeline().addLast(new JacksonCodec<>(WormholeMessage.class));

        // add ARQ to make sure messages arrive
        ch.pipeline().addFirst(new ByteToStopAndWaitArqDataCodec());
        ch.pipeline().addFirst(new StopAndWaitArqHandler(ARQ_RETRY_TIMEOUT_MILLIS));
        ch.pipeline().addFirst(new StopAndWaitArqCodec());
    }
}
