package org.drasyl.jtasklet.cli;

import org.drasyl.cli.converter.IdentityPublicKeyConverter;
import org.drasyl.cli.converter.InetSocketAddressConverter;
import org.drasyl.cli.node.IdentityPublicKeyMixin;
import org.drasyl.handler.PeersRttHandler.PeerRtt;
import org.drasyl.handler.PeersRttReport;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.jtasklet.broker.BrokerCommand;
import org.drasyl.jtasklet.consumer.ConsumerCommand;
import org.drasyl.jtasklet.consumer.GreyFilterCommand;
import org.drasyl.jtasklet.provider.ProviderCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;

import java.net.InetSocketAddress;

import static org.drasyl.node.JSONUtil.JACKSON_MAPPER;

@Command(
        name = "jtasklet",
        subcommands = {
                BrokerCommand.class,
                ComputeCommand.class,
                ConsumerCommand.class,
                GreyFilterCommand.class,
                HelpCommand.class,
                ProviderCommand.class
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
