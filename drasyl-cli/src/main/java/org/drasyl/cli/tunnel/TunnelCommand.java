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
package org.drasyl.cli.tunnel;

import picocli.CommandLine.HelpCommand;

import static picocli.CommandLine.Command;

/**
 * This command can be used to expose a locally TCP-based service over the overlay network to any
 * other computer running drasyl.
 * <pre>
 * +-------------------+          +-----------------------------+                  +------------------------------+          +-------------------+
 * | local TCP service |  expose  |           local             |  drasyl overlay  |            remote            |  expose  |    exposed TCP    |
 * | (behind firewall) |--------->| {@link TunnelExposeCommand} |----------------->| {@link TunnelConsumeCommand} |--------->|  service (local)  |
 * +-------------------+          +-----------------------------+                  +------------------------------+          +-------------------+
 * </pre>
 */
@Command(
        name = "tunnel",
        header = "Expose safely local networked services behind through NATs and firewalls to other computers.",
        synopsisHeading = "%nUsage: ",
        commandListHeading = "%nCommands:%n",
        subcommands = {
                HelpCommand.class,
                TunnelConsumeCommand.class,
                TunnelExposeCommand.class
        }
)
public class TunnelCommand {
}
