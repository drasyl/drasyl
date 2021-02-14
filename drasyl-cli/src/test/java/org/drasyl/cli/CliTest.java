/*
 * Copyright (c) 2020-2021.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.drasyl.cli;

import org.drasyl.cli.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintStream;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CliTest {
    @Mock
    private PrintStream err;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Map<String, Command> commands;
    @Mock
    private Command command;
    @InjectMocks
    private Cli underTest;

    @Test
    void runShouldExecuteHelpCommandIfNothingIsGiven() {
        when(commands.get("help")).thenReturn(command);

        underTest.run(new String[]{});

        verify(commands.get("help")).execute(new String[]{});
    }

    @Test
    void runShouldExecuteGivenCommand() {
        when(commands.get("version")).thenReturn(command);

        underTest.run(new String[]{ "version" });

        verify(commands.get("version")).execute(new String[]{ "version" });
    }

    @Test
    void runShouldThrowExceptionForUnknownCommand() {
        underTest.run(new String[]{ "sadassdaashdaskj" });

        verify(err).println("ERR: Unknown command \"sadassdaashdaskj\" for \"drasyl\".");
    }
}
