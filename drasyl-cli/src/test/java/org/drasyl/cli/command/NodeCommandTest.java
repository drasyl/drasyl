package org.drasyl.cli.command;

import org.apache.commons.cli.CommandLine;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylNode;
import org.drasyl.cli.CliException;
import org.drasyl.util.Pair;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NodeCommandTest {
    private PrintStream printStream;
    @Mock
    private Function<DrasylConfig, Pair<DrasylNode, CompletableFuture<Void>>> nodeSupplier;
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
            assertThat(output, containsString("Usage:" + System.lineSeparator()));
            assertThat(output, containsString("drasyl node [flags]" + System.lineSeparator()));
            assertThat(output, containsString("Flags:" + System.lineSeparator()));
        }
    }

    @Nested
    class Execute {
        @Mock
        private CommandLine cmd;

        @Test
        void shouldRunANode(@Mock Pair<DrasylNode, CompletableFuture<Void>> pair) throws CliException {
            when(nodeSupplier.apply(any())).thenReturn(pair);
            when(pair.second()).thenReturn(completedFuture(null));

            underTest.execute(cmd);

            verify(nodeSupplier).apply(any());
        }
    }
}