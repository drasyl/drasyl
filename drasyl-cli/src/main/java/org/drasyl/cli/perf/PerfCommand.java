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
package org.drasyl.cli.perf;

import org.drasyl.handler.connection.ConnectionConfig;
import picocli.CommandLine.HelpCommand;

import static org.drasyl.handler.connection.ConnectionConfig.DRASYL_HDR_SIZE;
import static org.drasyl.handler.connection.ConnectionConfig.IP_MTU;
import static picocli.CommandLine.Command;

/**
 * Inspired by <a href="https://iperf.fr/iperf-download.php">https://iperf.fr/iperf-download.php</a>.
 */
@Command(
        name = "perf",
        header = {
                "Tool for measuring network performance.",
                "A specified number of messages per second of the desired size are sent to another node over a defined period of time. The amount of transferred data, the bitrate, as well as the number of lost and (out of order) delivered messages, are recorded"
        },
        synopsisHeading = "%nUsage: ",
        commandListHeading = "%nCommands:%n",
        subcommands = {
                HelpCommand.class,
                PerfClientCommand.class,
                PerfServerCommand.class
        }
)
public class PerfCommand {
    // reduce MMS by 4 bytes (perf header length)
    public static final ConnectionConfig CONNECTION_CONFIG = ConnectionConfig.newBuilder()
            .mmsS(IP_MTU - DRASYL_HDR_SIZE - 4)
            .mmsR(IP_MTU - DRASYL_HDR_SIZE - 4)
            .build();
}
