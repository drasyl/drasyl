/*
 * Copyright (c) 2020.
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

import org.apache.commons.cli.HelpFormatter;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CliTest {
    @Mock
    private HelpFormatter formatter;
    @Mock
    private Supplier<String> versionSupplier;
    @Mock
    private DrasylFunction<DrasylConfig, DrasylNode> nodeSupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylNode node;

    @ParameterizedTest
    @ValueSource(strings = { "-h", "--help" })
    void runShouldDisplayHelpIfRequested(String argument) throws CliException {
        Cli cli = new Cli(formatter, versionSupplier, nodeSupplier, node);
        cli.run(new String[]{ argument });

        verify(formatter).printHelp(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "-v", "--version" })
    void runShouldDisplayVersionIfRequested(String argument) throws CliException {
        Cli cli = new Cli(formatter, versionSupplier, nodeSupplier, node);
        cli.run(new String[]{ argument });

        verify(versionSupplier).get();
    }

    @Test
    void runShouldStartNode() throws CliException, DrasylException {
        when(nodeSupplier.apply(any())).thenReturn(node);

        Cli cli = new Cli(formatter, versionSupplier, nodeSupplier, node);
        cli.run(new String[]{});

        verify(nodeSupplier).apply(any());
    }
}