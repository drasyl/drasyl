package city.sane.akka.p2p.transport.relay.handler;

import city.sane.akka.p2p.transport.direct.messages.AkkaMessage;
import org.drasyl.core.common.handler.SimpleChannelDuplexHandler;
import org.drasyl.core.common.messages.ForwardableMessage;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.RelayException;
import org.drasyl.core.common.models.SessionUID;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This handler translates the AkkaMessages to relay messages and vice versa.
 */
public class RelayMessageHandler extends SimpleChannelDuplexHandler<Message, AkkaMessage> {
    private final Logger log = LoggerFactory.getLogger(RelayMessageHandler.class);

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, AkkaMessage akkaMessage) {
        String relayRecipientValue = akkaMessage.getRecipientSystem();
        SessionUID relayRecipient;
        switch (relayRecipientValue) {
            case "ALL":
                relayRecipient = SessionUID.ALL;
                break;

            case "ANY":
                relayRecipient = SessionUID.ANY;
                break;

            default:
                relayRecipient = SessionUID.of(relayRecipientValue);
        }
        // add serialized message to relay message
        Request relayMessage = new Request<>(new ForwardableMessage(
                SessionUID.of(akkaMessage.getSenderSystem()),
                relayRecipient,
                akkaMessage.getBlob()));

        // send message
        ctx.writeAndFlush(relayMessage);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Message message) throws RelayMessageHandlerException {
        log.debug("New Message: {}", message);
        if (message instanceof ForwardableMessage) {
            ForwardableMessage fMsg = (ForwardableMessage) message;
            ctx.fireChannelRead(new AkkaMessage(
                    fMsg.getBlob(),
                    fMsg.getSenderUID().getValue(),
                    fMsg.getReceiverUID().getValue()
            ));
        }
        else if (message instanceof RelayException) {
            throw new RelayMessageHandlerException(((RelayException) message).getException());
        }
        else {
            log.debug("Discard unknown received message: {}", message);
        }
    }
}
