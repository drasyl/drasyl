/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.cli.node;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.channel.OverlayAddressedMessage;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requireNonNegative;

public final class ActivityPattern {
    private static final Logger LOG = LoggerFactory.getLogger(ActivityPattern.class);

    private ActivityPattern() {
    }

    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(name = "sleep", value = SleepActivity.class),
            @Type(name = "send", value = SendActivity.class),
            @Type(name = "goto", value = GotoActivity.class)
    })
    @JsonIgnoreProperties(ignoreUnknown = true)
    interface Activity {
        void perform(final ChannelHandlerContext ctx, final ActivityPatternHandler handler);
    }

    /**
     * Activity that let the node sleep for given duration.
     */
    static class SleepActivity implements Activity {
        private final long duration;

        @JsonCreator
        public SleepActivity(@JsonProperty("duration") final long duration) {
            this.duration = requireNonNegative(duration);
        }

        /**
         * @return Sleep duration in ms.
         */
        public long getDuration() {
            return duration;
        }

        @Override
        public void perform(final ChannelHandlerContext ctx, final ActivityPatternHandler handler) {
            LOG.info("[{}] Sleep for {}ms.", handler.index - 1, duration);
            ctx.executor().schedule(() -> handler.doNextActivity(ctx), duration, MILLISECONDS);
        }
    }

    /**
     * Activity that let the node send a message to a peer.
     */
    static class SendActivity implements Activity {
        private final DrasylAddress recipient;
        private final String payload;

        @JsonCreator
        SendActivity(@JsonProperty("recipient") final DrasylAddress recipient,
                     @JsonProperty("payload") final String payload) {
            this.recipient = requireNonNull(recipient);
            this.payload = payload;
        }

        @JsonDeserialize(as = IdentityPublicKey.class)
        public DrasylAddress getRecipient() {
            return recipient;
        }

        public String getPayload() {
            return payload;
        }

        @Override
        public void perform(final ChannelHandlerContext ctx, final ActivityPatternHandler handler) {
            final ByteBuf byteBuf = ctx.alloc().buffer(getPayload().length());
            byteBuf.writeCharSequence(getPayload(), UTF_8);
            final int activityIndex = handler.index - 1;
            LOG.info("[{}] Send peer `{}` message `{}`.", activityIndex, getRecipient(), byteBuf);
            ctx.pipeline().writeAndFlush(new OverlayAddressedMessage<>(byteBuf, getRecipient(), (DrasylAddress) ctx.channel().localAddress())).addListener(future -> {
                if (!future.isSuccess()) {
                    LOG.warn("[{}] Unable to send peer `{}` message `{}`: `{}`", () -> activityIndex, this::getRecipient, this::getPayload, future::cause);
                }
            });
            handler.doNextActivity(ctx);
        }
    }

    /**
     * Logical activity, that will control that activity is performed next. Can be used to create
     * loops.
     */
    static class GotoActivity implements Activity {
        private final int gotoIndex;

        @JsonCreator
        GotoActivity(@JsonProperty("goto") final int gotoIndex) {
            this.gotoIndex = requireNonNegative(gotoIndex);
        }

        public int getGoto() {
            return gotoIndex;
        }

        @Override
        public void perform(final ChannelHandlerContext ctx, final ActivityPatternHandler handler) {
            LOG.info("[{}] Go to activity `{}`.", handler.index - 1, gotoIndex);
            handler.index = gotoIndex;
            handler.doNextActivity(ctx);
        }
    }
}
