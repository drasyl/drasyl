package city.sane.akka.p2p.transport.direct.handler;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import city.sane.akka.p2p.P2PActorRef;
import city.sane.akka.p2p.transport.InboundMessageEnvelope;
import city.sane.akka.p2p.transport.OutboundMessageEnvelope;
import city.sane.akka.p2p.transport.P2PTransport;
import city.sane.akka.p2p.transport.direct.AbstractDirectP2PTransportChannel;
import city.sane.akka.p2p.transport.direct.messages.SystemNameMessage;
import org.drasyl.core.common.handler.SimpleChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;


public class DirectPeerHandler extends SimpleChannelDuplexHandler<InboundMessageEnvelope, SystemNameMessage> {

    private final String systemName;
    private final boolean isChannelInitiator;
    private final P2PTransport transport;
    private AbstractDirectP2PTransportChannel parentChannel;
    private String peerSystemName;


    public DirectPeerHandler(String systemName,
                             P2PTransport transport,
                             AbstractDirectP2PTransportChannel parentChannel,
                             boolean isChannelInitiator) {
        this.systemName = systemName;
        this.transport = transport;
        this.parentChannel = parentChannel;
        this.isChannelInitiator = isChannelInitiator;
    }

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, SystemNameMessage msg) {

        ctx.writeAndFlush(new OutboundMessageEnvelope(
                msg,
                ActorRef.noSender(),
                new P2PActorRef(transport, ActorPath.fromString("bud://ALL"))
        ));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, InboundMessageEnvelope message) {
        if (message.getDeserializedMessage() instanceof SystemNameMessage) {
            String remoteSystemName = ((SystemNameMessage) message.getDeserializedMessage()).getSystemName();
            if (peerSystemName == null) {
                peerSystemName = remoteSystemName;
                parentChannel.addPeer(peerSystemName, ctx.channel());
            } else {
                parentChannel.changePeerSystemName(peerSystemName, remoteSystemName);
            }
            if (!isChannelInitiator) {
                // respond to initiator
                channelWrite0(ctx, new SystemNameMessage(this.systemName));
            }
        } else {
            parentChannel.receive(message);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (isChannelInitiator) {
            // inform channel about own system name
            channelWrite0(ctx, new SystemNameMessage(systemName));
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        parentChannel.removePeer(peerSystemName);
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        parentChannel.notifyError(
                String.format("An error has occurred in the direct connection to %s. Close connection.", peerSystemName),
                cause);
        ctx.close();
    }

}
