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

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.DefaultEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.ScheduledFuture;
import io.netty.util.internal.StringUtil;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.util.Preconditions.requirePositive;

public class ConnectionAnalyzeHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(ConnectionAnalyzeHandler.class);
    private final long interval;
    private final Function<ChannelHandlerContext, AnalyzeLogger> loggerSupplier;
    private EventExecutor executor;
    private AnalyzeLogger logger;
    private ConnectionHandler handler;
    private ScheduledFuture<?> task;

    public ConnectionAnalyzeHandler(final EventExecutor executor,
                                    final long interval,
                                    final Function<ChannelHandlerContext, AnalyzeLogger> loggerSupplier,
                                    final AnalyzeLogger logger,
                                    final ConnectionHandler handler) {
        this.executor = executor;
        this.interval = requirePositive(interval);
        this.loggerSupplier = requireNonNull(loggerSupplier);
        this.logger = logger;
        this.handler = handler;
    }

    public ConnectionAnalyzeHandler() {
        this(new DefaultEventLoop(), 10, ctx -> {
            final String filename = StringUtil.simpleClassName(ConnectionAnalyzeHandler.class) + "_id" + ctx.channel().id() + "_L" + ctx.channel().localAddress() + "_R" + ctx.channel().remoteAddress();
            return new CsvAnalyzeLogger("./" + filename + ".csv");
        }, null, null);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        startTask(ctx);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) {
        cancelTask(ctx);
        ctx.fireChannelInactive();
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            startTask(ctx);
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTask(ctx);
    }

    private void startTask(final ChannelHandlerContext ctx) {
        if (task == null) {
            if (handler == null) {
                // search handler in channel
                handler = ctx.pipeline().get(ConnectionHandler.class);
            }

            if (handler != null) {
                if (executor == null) {
                    // use channel executor
                    executor = ctx.executor();
                }

                logger = loggerSupplier.apply(ctx);

                task = executor.scheduleAtFixedRate(() -> {
                    ConnectionHandshakeStatus status = handler.userCallStatus();
                    logger.log(status.tcb());
                }, interval, interval, MILLISECONDS);
            }
            else {
                LOG.warn("No {} handler found in channel {}.", StringUtil.simpleClassName(this), ctx.channel());
                ctx.pipeline().remove(this);
            }
        }
    }

    @SuppressWarnings("unused")
    private void cancelTask(final ChannelHandlerContext ctx) {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public interface AnalyzeLogger {
        void log(final TransmissionControlBlock tcb);
    }

    public static class CsvAnalyzeLogger implements AnalyzeLogger {
        public static final long PID = ProcessHandle.current().pid();
        private final FileWriter writer;
        private boolean headerWritten;

        public CsvAnalyzeLogger(final String fileName) {
            try {
                headerWritten = new File(fileName).exists();
                writer = new FileWriter(fileName, true);
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void log(final TransmissionControlBlock tcb) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("pid", PID);
            entry.put("time", RFC_1123_DATE_TIME.format(ZonedDateTime.now()));

            // RFC 9293: Send Sequence Variables
            entry.put("SND.UNA", tcb.sndUna());
            entry.put("SND.NXT", tcb.sndNxt());
            entry.put("SND.WND", tcb.sndWnd());
            entry.put("SND.WL1", tcb.sndWl1());
            entry.put("SND.WL2", tcb.sndWl2());
            entry.put("ISS", tcb.iss());
            entry.put("SND.BUF", tcb.sendBuffer().length());

            // RFC 9293: Receive Sequence Variables
            entry.put("RCV.NXT", tcb.rcvNxt());
            entry.put("RCV.WND", tcb.rcvWnd());
            entry.put("IRS", tcb.irs());
            entry.put("RCV.BUF", tcb.receiveBuffer().bytes());

            entry.put("SendMSS", tcb.sendMss());
            entry.put("Max(SND.WND)", tcb.maxSndWnd());

            // RFC 7323: Timestamps option
            entry.put("TS.Recent", tcb.tsRecent());
            entry.put("Last.ACK.sent", tcb.lastAckSent());
            entry.put("Snd.TS.OK", tcb.sndTsOk());

            // RFC 6298: Retransmission Timer Computation
            entry.put("RTTVAR", tcb.rttVar());
            entry.put("SRTT", tcb.sRtt());
            entry.put("RTO", tcb.rto());

            // RFC 5681: Congestion Control Algorithms
            entry.put("cwnd", tcb.cwnd());
            entry.put("ssthresh", tcb.ssthresh());

            try {
                // header
                if (!headerWritten) {
                    headerWritten = true;
                    boolean firstColumn = true;
                    for (final String key : entry.keySet()) {
                        if (firstColumn) {
                            firstColumn = false;
                        }
                        else {
                            writer.append(",");
                        }
                        escapedWrite(writer, key);
                    }
                    writer.append('\n');
                    writer.flush();
                }

                // row
                boolean firstColumn2 = true;
                for (final Object value : entry.values()) {
                    if (firstColumn2) {
                        firstColumn2 = false;
                    }
                    else {
                        writer.append(",");
                    }
                    escapedWrite(writer, value);
                }
                writer.append('\n');
                writer.flush();
            }
            catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void escapedWrite(final FileWriter writer,
                                  final CharSequence value) throws IOException {
            writer.append("\"");
            writer.append(value);
            writer.append("\"");
        }

        private void escapedWrite(final FileWriter writer, final Object value) throws IOException {
            escapedWrite(writer, value != null ? value.toString() : "");
        }
    }
}
