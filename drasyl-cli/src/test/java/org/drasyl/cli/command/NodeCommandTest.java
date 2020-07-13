package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.util.DrasylFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NodeCommandTest {
    @Mock
    private PrintStream printStream;
    @Mock
    private DrasylFunction<DrasylConfig, DrasylNode, DrasylException> nodeSupplier;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DrasylNode node;
    @InjectMocks
    private NodeCommand underTest;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    void setUp() {
        outputStream = new ByteArrayOutputStream();
        printStream = new PrintStream(outputStream, true);
        underTest = new NodeCommand(printStream, nodeSupplier, node);
    }

    @Nested
    class Help {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldPrintHelp() {
            underTest.help(cmd);

            String output = outputStream.toString();
            assertThat(output, containsString("Run a drasyl node in the current directory."));
            assertThat(output, containsString("Usage:\n"));
            assertThat(output, containsString("drasyl node [flags]\n"));
            assertThat(output, containsString("Flags:\n"));
            assertThat(output, containsString("syntax:\n"));
        }
    }

    @Nested
    class Execute {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldRunANode() throws CliException, DrasylException {
            when(nodeSupplier.apply(any())).thenReturn(node);

            underTest.execute(cmd);

            verify(nodeSupplier.apply(any())).start();
        }
    }
}
