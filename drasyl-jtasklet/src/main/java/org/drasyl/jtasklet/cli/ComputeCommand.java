package org.drasyl.jtasklet.cli;

import org.drasyl.jtasklet.provider.runtime.ExecutionResult;
import org.drasyl.jtasklet.provider.runtime.GraalVmJsRuntimeEnvironment;
import org.drasyl.jtasklet.provider.runtime.VNMIFERuntimeEnvironment;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "compute",
        description = {
                "Computes Tasklets locally Tasklet VM"
        },
        showDefaultValues = true
)
public class ComputeCommand implements Callable<Integer> {
    private static final VNMIFERuntimeEnvironment RUNTIME_ENVIRONMENT = new VNMIFERuntimeEnvironment();
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
        for (int i = 0; i < 3; i++) {
            final long startTime = System.currentTimeMillis();
            final ExecutionResult result = RUNTIME_ENVIRONMENT.execute(task, input != null ? input.toArray() : new Object[0]);
            System.out.println("Output: " + Arrays.toString(result.getOutput()));
            final long endTime = System.currentTimeMillis();
            long execTime = endTime - startTime;
            System.out.println("Time: " + execTime);
        }

        return 0;
    }
}
