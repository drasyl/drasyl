package org.drasyl.jtasklet.cli;

import org.drasyl.cli.converter.IdentityPublicKeyConverter;
import org.drasyl.cli.converter.InetSocketAddressConverter;
import org.drasyl.cli.node.IdentityPublicKeyMixin;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttHandler.PeersRttReport;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand;
import org.drasyl.jtasklet.consumer.GreyFilterCommand;
import org.drasyl.jtasklet.consumer.OffloadCommand;
import org.drasyl.jtasklet.consumer.OffloadRcJsonRpcCommand;
import org.drasyl.jtasklet.provider.VmCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.net.InetSocketAddress;

import static org.drasyl.node.JsonUtil.JACKSON_MAPPER;

@Command(
        name = "jtasklet",
        subcommands = {
                BrokerCommand.class,
                ComputeCommand.class,
                GreyFilterCommand.class,
                OffloadCommand.class,
                OffloadRcJsonRpcCommand.class,
                HelpCommand.class,
                VmCommand.class
        }
)
public class Cli {
    public static void main(final String[] args) {
        JACKSON_MAPPER.addMixIn(IdentityPublicKey.class, IdentityPublicKeyMixin.class);
        JACKSON_MAPPER.addMixIn(PeersRttReport.class, PeersRttReportMixin.class);
        JACKSON_MAPPER.addMixIn(PeerRtt.class, PeerRttMixin.class);

        final CommandLine commandLine = new CommandLine(new Cli());
        commandLine.registerConverter(IdentityPublicKey.class, new IdentityPublicKeyConverter());
        commandLine.registerConverter(InetSocketAddress.class, new InetSocketAddressConverter());
        commandLine.execute(args);
    }
}
