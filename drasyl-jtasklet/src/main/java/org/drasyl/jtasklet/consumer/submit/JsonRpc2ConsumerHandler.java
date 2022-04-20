package org.drasyl.jtasklet.consumer.submit;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.drasyl.cli.node.message.JsonRpc2Error;
import org.drasyl.cli.node.message.JsonRpc2Request;
import org.drasyl.cli.node.message.JsonRpc2Response;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.util.Arrays;
import java.util.List;

import static io.netty.channel.ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.node.message.JsonRpc2Error.METHOD_NOT_FOUND;
import static org.drasyl.jtasklet.util.SourceUtil.minifySource;

// { "jsonrpc": "2.0", "method": "offload", "params": {"task":"(function () {return [];});", "input":[]}, "id": 1}
public class JsonRpc2ConsumerHandler extends SimpleChannelInboundHandler<JsonRpc2Request> {
    private static final Logger LOG = LoggerFactory.getLogger(JsonRpc2ConsumerHandler.class);
    private final Channel taskletChannel;

    public JsonRpc2ConsumerHandler(final Channel taskletChannel) {
        this.taskletChannel = requireNonNull(taskletChannel);
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt instanceof TaskResult) {
            final JsonRpc2Response response = new JsonRpc2Response(((TaskResult) evt).output(), "123");
            LOG.trace("Send response `{}`.", response);
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
        else {
            ctx.fireUserEventTriggered(evt);
        }
    }

    @Override
    protected void channelRead0(final ChannelHandlerContext ctx,
                                final JsonRpc2Request request) {
        LOG.trace("Got request `{}`.", request);

        final String method = request.getMethod();
        if ("offload".equals(method)) {
            offload(request);
        }
        else {
            final JsonRpc2Error error = new JsonRpc2Error(METHOD_NOT_FOUND, "the method '" + method + "' does not exist / is not available.");
            final JsonRpc2Response response = new JsonRpc2Response(error, "");
            ctx.writeAndFlush(response).addListener(FIRE_EXCEPTION_ON_FAILURE);
        }
    }

    private void offload(final JsonRpc2Request request) {
        final String task = request.getParam("task");
        final Object[] input = request.getParam("input", List.of()).toArray();
        LOG.info("Task  : {}", () -> minifySource(task));
        LOG.info("Input : {}", () -> Arrays.toString(input));

        taskletChannel.pipeline().fireUserEventTriggered(new SubmitTask(task, input));
    }
}
