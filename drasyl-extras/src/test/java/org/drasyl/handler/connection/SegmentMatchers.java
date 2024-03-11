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
package org.drasyl.handler.connection;

import io.netty.buffer.ByteBuf;
import org.drasyl.handler.connection.SegmentOption.TimestampsOption;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.drasyl.handler.connection.Segment.ACK;
import static org.drasyl.handler.connection.Segment.FIN;
import static org.drasyl.handler.connection.Segment.PSH;
import static org.drasyl.handler.connection.Segment.RST;
import static org.drasyl.handler.connection.Segment.SYN;
import static org.drasyl.handler.connection.SegmentOption.MAXIMUM_SEGMENT_SIZE;
import static org.drasyl.handler.connection.SegmentOption.TIMESTAMPS;

public class SegmentMatchers {
    public static Matcher<Segment> srcPort(final int srcPort) {
        return new HasSrcPort(srcPort);
    }

    public static Matcher<Segment> dstPort(final int dstPort) {
        return new HasDstPort(dstPort);
    }

    public static Matcher<Segment> seq(final long seq) {
        return new HasSeq(seq);
    }

    public static Matcher<Segment> ack(final long ack) {
        return new HasAck(ack);
    }

    public static Matcher<Segment> ctl(final byte ctl) {
        return new HasCtl(ctl);
    }

    public static Matcher<Segment> ctl(final byte... flags) {
        return new HasCtl(flags);
    }

    public static Matcher<Segment> win(final long win) {
        return new HasWin(win);
    }

    public static Matcher<Segment> len(final int len) {
        return new HasLen(len);
    }

    public static Matcher<Segment> data(final ByteBuf data) {
        return new HasData(data);
    }

    public static Matcher<Segment> tsOpt(final long tsVal, final long tsEcr) {
        return new HasTimestampsOption(tsVal, tsEcr);
    }

    public static Matcher<Segment> tsOpt(final long tsVal) {
        return tsOpt(tsVal, 0);
    }

    public static Matcher<Segment> mss(final int mss) {
        return new HasMssOption(mss);
    }

    private static class HasSrcPort extends TypeSafeMatcher<Segment> {
        private final int srcPort;

        public HasSrcPort(final int srcPort) {
            this.srcPort = srcPort;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.srcPort() == srcPort;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("S=" + srcPort);
        }
    }

    private static class HasDstPort extends TypeSafeMatcher<Segment> {
        private final int dstPort;

        public HasDstPort(final int dstPort) {
            this.dstPort = dstPort;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.dstPort() == dstPort;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("D=" + dstPort);
        }
    }

    private static class HasSeq extends TypeSafeMatcher<Segment> {
        private final long seq;

        public HasSeq(final long seq) {
            this.seq = seq;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.seq() == seq;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("SEQ=" + seq);
        }
    }

    private static class HasAck extends TypeSafeMatcher<Segment> {
        private final long ack;

        public HasAck(final long ack) {
            this.ack = ack;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.ack() == ack;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("ACK=" + ack);
        }
    }

    private static class HasCtl extends TypeSafeMatcher<Segment> {
        private final byte ctl;

        public HasCtl(final byte... flags) {
            byte ctl = 0;
            for (byte flag : flags) {
                ctl |= flag;
            }
            this.ctl = ctl;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.ctl() == ctl;
        }

        @Override
        public void describeTo(final Description description) {
            final List<String> controlBitLabels = new ArrayList<>();
            if ((ctl & PSH) != 0) {
                controlBitLabels.add("PSH");
            }
            if ((ctl & RST) != 0) {
                controlBitLabels.add("RST");
            }
            if ((ctl & FIN) != 0) {
                controlBitLabels.add("FIN");
            }
            if ((ctl & SYN) != 0) {
                controlBitLabels.add("SYN");
            }
            if ((ctl & ACK) != 0) {
                controlBitLabels.add("ACK");
            }

            description.appendText("CTL=" + String.join(",", controlBitLabels));
        }
    }

    private static class HasWin extends TypeSafeMatcher<Segment> {
        private final long win;

        public HasWin(final long win) {
            this.win = win;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.wnd() == win;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("WIN=" + win);
        }
    }

    private static class HasData extends TypeSafeMatcher<Segment> {
        private final ByteBuf data;

        public HasData(final ByteBuf data) {
            this.data = requireNonNull(data);
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return data.equals(seg.content());
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("segment text is " + data);
        }
    }

    private static class HasLen extends TypeSafeMatcher<Segment> {
        private final int len;

        public HasLen(final int len) {
            this.len = len;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            return seg.len() == len;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("LEN=" + len);
        }
    }

    private static class HasTimestampsOption extends TypeSafeMatcher<Segment> {
        private final long tsVal;
        private final long tsEcr;

        public HasTimestampsOption(final long tsVal, final long tsEcr) {
            this.tsVal = tsVal;
            this.tsEcr = tsEcr;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            final TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
            return tsOpt != null && tsOpt.tsVal == tsVal && tsOpt.tsEcr == tsEcr;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("<TSval=" + tsVal + ",TSecr=" + tsEcr + ">");
        }
    }

    private static class HasMssOption extends TypeSafeMatcher<Segment> {
        private final int mss;

        public HasMssOption(final int mss) {
            this.mss = mss;
        }

        @Override
        protected boolean matchesSafely(final Segment seg) {
            final Integer mss = (Integer) seg.options().get(MAXIMUM_SEGMENT_SIZE);
            return mss != null && this.mss == mss;
        }

        @Override
        public void describeTo(final Description description) {
            description.appendText("<MAXIMUM_SEGMENT_SIZE=" + mss + ">");
        }
    }
}
