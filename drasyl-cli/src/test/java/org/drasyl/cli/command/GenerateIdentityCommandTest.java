package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@ExtendWith(MockitoExtension.class)
class GenerateIdentityCommandTest {
    private PrintStream printStream;
    @InjectMocks
    private GenerateIdentityCommand underTest;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new GenerateIdentityCommand(printStream);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            final String output = outputStream.toString();
            assertThat(output, containsString("Generate and output new Identity in JSON format."));
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl genidentity [flags]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }
}