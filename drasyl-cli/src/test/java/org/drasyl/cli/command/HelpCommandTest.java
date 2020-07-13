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
class HelpCommandTest {
    @Mock
    private PrintStream printStream;
    @InjectMocks
    private HelpCommand underTest;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new HelpCommand(printStream);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            String output = outputStream.toString();
            assertThat(output, containsString("drasyl is an general purpose transport overlay network."));
            assertThat(output, containsString("Usage:\n"));
            assertThat(output, containsString("drasyl help [flags]\n"));
            assertThat(output, containsString("Flags:\n"));
        }
    }

    @Nested
    class Execute {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintAvailableCommands() {
            underTest.execute(cmd);

            String output = outputStream.toString();
            assertThat(output, containsString("Usage:\n"));
            assertThat(output, containsString("drasyl [flags]\n"));
            assertThat(output, containsString("drasyl [command]\n"));
            assertThat(output, containsString("Available Commands:\n"));
            assertThat(output, containsString("Flags:\n"));
        }
    }
}