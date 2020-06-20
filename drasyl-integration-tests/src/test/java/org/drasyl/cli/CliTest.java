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
import org.drasyl.DrasylNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.function.Supplier;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CliTest {
    @Mock
    private HelpFormatter formatter;
    @Mock
    private Supplier<String> versionSupplier;
    @Mock
    private DrasylNode node;

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @ParameterizedTest
    @ValueSource(strings = { "-h", "--help" })
    void runShouldDisplayHelpIfRequested(String argument) throws CliException {
        Cli cli = new Cli(formatter, versionSupplier, node);
        cli.run(new String[]{ argument });

        verify(formatter).printHelp(any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "-v", "--version" })
    void runShouldDisplayVersionIfRequested(String argument) throws CliException {
        Cli cli = new Cli(formatter, versionSupplier, node);
        cli.run(new String[]{ argument });

        verify(versionSupplier).get();
    }
}