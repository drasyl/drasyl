package org.drasyl.util;

import org.drasyl.handler.connection.TransmissionControlBlock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;

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
        try {
            // header
            if (!headerWritten) {
                headerWritten = true;
                escapedWrite(writer, "pid");
                writer.append(",");
                escapedWrite(writer, "time");
                // Send Sequence Variables
                writer.append(",");
                escapedWrite(writer, "SND.UNA");
                writer.append(",");
                escapedWrite(writer, "SND.NXT");
                writer.append(",");
                escapedWrite(writer, "SND.WND");
                writer.append(",");
                escapedWrite(writer, "SND.WL1");
                writer.append(",");
                escapedWrite(writer, "SND.WL2");
                writer.append(",");
                escapedWrite(writer, "ISS");
                // Receive Sequence Variables
                writer.append(",");
                escapedWrite(writer, "RCV.NXT");
                writer.append(",");
                escapedWrite(writer, "RCV.WND");
                writer.append(",");
                escapedWrite(writer, "IRS");

                writer.append(",");
                escapedWrite(writer, "SND.BUF");
                writer.append(",");
                escapedWrite(writer, "OG.SEG.Q");
                writer.append(",");
                escapedWrite(writer, "RTNS.Q");
                writer.append(",");
                escapedWrite(writer, "RCV.BUF");

                writer.append(",");
                escapedWrite(writer, "CWND");
                writer.append(",");
                escapedWrite(writer, "SSTHRESH");

                writer.append('\n');
            }
            writer.flush();

            // row
            escapedWrite(writer, PID);
            writer.append(",");
            escapedWrite(writer, RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
            // Send Sequence Variables
            writer.append(",");
            escapedWrite(writer, tcb.sndUna());
            writer.append(",");
            escapedWrite(writer, tcb.sndNxt());
            writer.append(",");
            escapedWrite(writer, tcb.sndWnd());
            writer.append(",");
            escapedWrite(writer, tcb.sndWl1());
            writer.append(",");
            escapedWrite(writer, tcb.sndWl2());
            writer.append(",");
            escapedWrite(writer, tcb.iss());
            // Receive Sequence Variables
            writer.append(",");
            escapedWrite(writer, tcb.rcvNxt());
            writer.append(",");
            escapedWrite(writer, tcb.rcvWnd());
            writer.append(",");
            escapedWrite(writer, tcb.irs());

            writer.append(",");
            escapedWrite(writer, tcb.sendBuffer().readableBytes());
            writer.append(",");
            escapedWrite(writer, tcb.outgoingSegmentQueue().len());
            writer.append(",");
            escapedWrite(writer, tcb.retransmissionQueue().bytes());
            writer.append(",");
            escapedWrite(writer, tcb.receiveBuffer().readableBytes());

            writer.append(",");
            escapedWrite(writer, tcb.cwnd());
            writer.append(",");
            escapedWrite(writer, tcb.ssthresh());

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
