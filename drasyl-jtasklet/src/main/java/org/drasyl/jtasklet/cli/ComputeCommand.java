package org.drasyl.jtasklet.cli;

import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.UTF_8;

@Command(
        name = "compute",
        description = {
                "Computes Tasklets locally Tasklet VM"
        },
        showDefaultValues = true
)
public class ComputeCommand implements Callable<Integer> {
    private static final GraalVmJsRuntimeEnvironment RUNTIME_ENVIRONMENT = new GraalVmJsRuntimeEnvironment();
    @Option(
            names = { "--task" },
            required = true
    )
    private Path task;
    @Parameters
    List<Object> input;

    @Override
    public Integer call() throws Exception {
        System.out.println("Input: " + Arrays.toString(input != null ? (input.toArray()) : new Object[0]));
        final String source = Files.readString(task, UTF_8);
        for (int i = 0; i < 3; i++) {
            final long startTime = System.currentTimeMillis();
            final Object[] output = RUNTIME_ENVIRONMENT.execute(source, input != null ? input.toArray() : new Object[0]);
            System.out.println("Output: " + Arrays.toString(output));
            final long endTime = System.currentTimeMillis();
            long execTime = endTime - startTime;
            System.out.println("Time: " + execTime);
        }

        return 0;
    }
}
