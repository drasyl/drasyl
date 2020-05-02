package org.drasyl.core.client.transport.relay.handler;

import org.drasyl.core.client.transport.direct.messages.AkkaMessage;
import org.drasyl.core.common.handler.SimpleChannelDuplexHandler;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.IMessage;
import org.drasyl.core.common.messages.NodeServerException;
import io.netty.channel.ChannelHandlerContext;
import org.drasyl.core.node.identity.Identity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler translates the AkkaMessages to relay messages and vice versa.
 */
public class RelayMessageHandler extends SimpleChannelDuplexHandler<IMessage, AkkaMessage> {
    private final Logger log = LoggerFactory.getLogger(RelayMessageHandler.class);

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, AkkaMessage akkaMessage) {
        String relayRecipientValue = akkaMessage.getRecipientSystem();
        Identity relayRecipient = Identity.of(relayRecipientValue);

        // add serialized message to relay message
        Request relayMessage = new Request<>(new Message(
                Identity.of(akkaMessage.getSenderSystem()),
                relayRecipient,
                akkaMessage.getBlob()));

        // sendMSG message
        ctx.writeAndFlush(relayMessage);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, IMessage message) throws RelayMessageHandlerException {
        log.debug("New Message: {}", message);
        if (message instanceof Message) {
            Message fMsg = (Message) message;
            ctx.fireChannelRead(new AkkaMessage(
                    fMsg.getPayload(),
                    fMsg.getSender().getId(),
                    fMsg.getRecipient().getId()
            ));
        }
        else if (message instanceof NodeServerException) {
            throw new RelayMessageHandlerException(((NodeServerException) message).getException());
        }
        else {
            log.debug("Discard unknown received message: {}", message);
        }
    }
}
