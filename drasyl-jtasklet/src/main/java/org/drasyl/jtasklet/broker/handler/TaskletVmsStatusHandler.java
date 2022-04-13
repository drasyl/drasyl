package org.drasyl.jtasklet.broker.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.TaskletVm;

import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TaskletVmsStatusHandler extends ChannelInboundHandlerAdapter {
    private final PrintStream out;
    private final Map<IdentityPublicKey, TaskletVm> vms;

    public TaskletVmsStatusHandler(final PrintStream out,
                                   Map<IdentityPublicKey, TaskletVm> vms) {
        this.out = requireNonNull(out);
        this.vms = requireNonNull(vms);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        ctx.executor().scheduleWithFixedDelay(this::printVms, 5_000L, 5_000L, MILLISECONDS);
        ctx.fireChannelActive();
    }

    private void printVms() {
        synchronized (vms) {
            final StringBuilder builder = new StringBuilder();

            // table header
            final ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.now(), Clock.systemDefaultZone().getZone());
            builder.append(String.format("Time: %-35s%n", RFC_1123_DATE_TIME.format(zonedDateTime)));
            builder.append(String.format("%-64s  %9s  %5s  %6s  %9s  %6s%n", "Tasklet VM", "Heartbeat", "State", "Tasks#", "Benchmark", "Token"));

            // table body
            for (final Map.Entry<IdentityPublicKey, TaskletVm> entry : vms.entrySet()) {
                final IdentityPublicKey address = entry.getKey();
                final TaskletVm vm = entry.getValue();

                // table row
                builder.append(String.format(
                        "%-64s  %7dms  %5s  %6d  %9d  %6s%n",
                        address,
                        vm.timeSinceLastHeartbeat(),
                        vm.isStale() ? "Stale" : (vm.isBusy() ? "Busy" : "Idle"),
                        vm.getComputations(),
                        vm.getBenchmark(),
                        vm.getToken()
                ));
            }

            out.println(builder);
        }
    }
}
