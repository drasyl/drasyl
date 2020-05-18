package org.drasyl.cli;

import org.drasyl.DrasylNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;


public class CliIT {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private Cli cli;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);

        if (cli != null) {
            cli.shutdown();
        }
    }

    @Test
    public void runShouldPrintHelp() throws CliException {
        cli = new Cli();
        cli.run(new String[]{ "--help" });

        assertThat(outContent.toString(), containsString("Usage:"));
    }

    @Test
    public void runShouldPrintVersion() throws CliException {
        cli = new Cli();
        cli.run(new String[]{ "--version" });

        assertThat(outContent.toString(), containsString(DrasylNode.getVersion()));
    }
}
