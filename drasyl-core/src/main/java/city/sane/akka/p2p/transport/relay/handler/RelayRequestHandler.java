package city.sane.akka.p2p.transport.relay.handler;

import org.drasyl.core.common.handler.SimpleChannelDuplexHandler;
import org.drasyl.core.common.messages.Message;
import org.drasyl.core.common.messages.Response;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * This handler sends requests to the relay and waits for the corresponding response from the relay. The response is then mapped to the request.
 */
public class RelayRequestHandler extends SimpleChannelDuplexHandler<Response, Request> {
    private final Logger log = LoggerFactory.getLogger(RelayRequestHandler.class);
    private final Map<String, Request> requests = new HashMap<>();

    @Override
    protected void channelWrite0(ChannelHandlerContext ctx, Request request) {
        Message message = request.getMessage();
        String messageID = message.getMessageID();
        log.debug("Send request with id {}: {}", messageID, message);
        requests.put(messageID, request);
        ReferenceCountUtil.release(request);
        ctx.write(message);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Response response) {
        Message message = response.getMessage();
        String messageID = response.getMsgID();
        Request request = requests.remove(messageID);
        if (request != null) {
            log.debug("Received response for id {}: {}", messageID, message);
            request.completeRequest(message);
            ctx.fireChannelRead(response);
        }
        else {
            log.debug("Unknown response with id {} received: ", messageID, message);
            ReferenceCountUtil.release(response);
        }
    }
}
