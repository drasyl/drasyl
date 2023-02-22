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

    public static Matcher<Segment> window(final long window) {
        return new HasWindow(window);
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

    private static class HasSeq extends TypeSafeMatcher<Segment> {
        private final long seq;

        public HasSeq(final long seq) {
            this.seq = seq;
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            return seg.seq() == seq;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("SEQ=" + seq);
        }
    }

    private static class HasAck extends TypeSafeMatcher<Segment> {
        private final long ack;

        public HasAck(final long ack) {
            this.ack = ack;
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            return seg.ack() == ack;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("ACK=" + ack);
        }
    }

    private static class HasCtl extends TypeSafeMatcher<Segment> {
        private final byte ctl;

        public HasCtl(final byte... flags) {
            byte ctl = 0;
            for (int i = 0; i < flags.length; i++) {
                ctl |= flags[i];
            }
            this.ctl = ctl;
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            return seg.ctl() == ctl;
        }

        @Override
        public void describeTo(Description description) {
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

    private static class HasWindow extends TypeSafeMatcher<Segment> {
        private final long window;

        public HasWindow(final long window) {
            this.window = window;
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            return seg.wnd() == window;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("WIN=" + window);
        }
    }

    private static class HasData extends TypeSafeMatcher<Segment> {
        private final ByteBuf data;

        public HasData(final ByteBuf data) {
            this.data = requireNonNull(data);
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            return data.equals(seg.content());
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("segment text is " + data);
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
        protected boolean matchesSafely(Segment seg) {
            TimestampsOption tsOpt = (TimestampsOption) seg.options().get(TIMESTAMPS);
            return tsOpt != null && tsOpt.tsVal == tsVal && tsOpt.tsEcr == tsEcr;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("<TSval=" + tsVal + ",TSecr=" + tsEcr + ">");
        }
    }

    private static class HasMssOption extends TypeSafeMatcher<Segment> {
        private final int mss;

        public HasMssOption(final int mss) {
            this.mss = mss;
        }

        @Override
        protected boolean matchesSafely(Segment seg) {
            Integer mss = (Integer) seg.options().get(MAXIMUM_SEGMENT_SIZE);
            return mss != null && this.mss == mss;
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("<MSS=" + mss + ">");
        }
    }
}
