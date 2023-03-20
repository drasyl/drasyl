package org.drasyl.util;

import org.drasyl.handler.connection.TransmissionControlBlock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;

public class CsvLogger {
    public static final long PID = ProcessHandle.current().pid();
    private final FileWriter writer;
    private boolean headerWritten;

    public CsvLogger(final String fileName) {
        try {
            headerWritten = new File(fileName).exists();
            writer = new FileWriter(fileName, true);
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

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

        // RFC 9293: Receive Sequence Variables
        entry.put("RCV.NXT", tcb.rcvNxt());
        entry.put("RCV.WND", tcb.rcvWnd());
        entry.put("IRS", tcb.irs());

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
