package org.drasyl.cli.sdo.handler.policy;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import org.drasyl.channel.tun.Tun4Packet;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;
import org.luaj.vm2.ast.Str;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.drasyl.cli.sdo.handler.SdoPoliciesHandler.STORE;

public class ComputationResultMessageParserPolicyHandler extends ChannelDuplexHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ComputationResultMessageParserPolicyHandler.class);
    public static final String RESULT_START = "result_start848608910";
    public static final String RESULT_END = "result_end848608910";
    private final List<Map<String, String>> results = new ArrayList<>();

    public ComputationResultMessageParserPolicyHandler() {
        STORE.put("computation", results);
    }

    @Override
    public void write(final ChannelHandlerContext ctx,
                      final Object msg,
                      final ChannelPromise promise) throws Exception {
        checkForOffloadPacket(msg);
        checkForResultPacket(msg);

        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        checkForOffloadPacket(msg);
        checkForResultPacket(msg);

        ctx.fireChannelRead(msg);
    }

    private void checkForOffloadPacket(final Object msg) {
        if (msg instanceof Tun4Packet) {
            final Tun4Packet packet = (Tun4Packet) msg;
            final String packetContentString = packet.content().toString(UTF_8);
            //LOG.error("channelRead: msg = {}; content = {}; hexDump = {};", packet, packet.content(), ByteBufUtil.hexDump(packet.content()));
            final int index = packetContentString.indexOf("offload1222839438");
            if (index != -1) {
                LOG.error("COMPUTATION: {} -> {}: Offload packet.", packet.sourceAddress(), packet.destinationAddress());
            }
        }
    }

    private void checkForResultPacket(final Object msg) {
        if (msg instanceof Tun4Packet) {
            final Tun4Packet packet = (Tun4Packet) msg;
            final String packetContentString = packet.content().toString(UTF_8);
            final int start_index = packetContentString.indexOf(RESULT_START);
            final int end_index = packetContentString.indexOf(RESULT_END);
            //LOG.error("COMPUTING: {} -> {}: write: msg = {}; content = {}; hexDump = {}; string = {}", packet, packet.content(), ByteBufUtil.hexDump(packet.content()), packetContentString);
            if (start_index != -1 && end_index != -1) {
//                LOG.error("COMPUTING: result");
                final String resultString = packetContentString.substring(start_index + RESULT_START.length() + 1, end_index - 1);
                final Map<String, String> result = new HashMap<>();
                final String[] pairs = resultString.split(";");
                for (final String pair : pairs) {
                    final String[] keyValue = pair.split("=", 2);
                    result.put(keyValue[0], keyValue[1]);
                }
                LOG.error("COMPUTATION: {} -> {}: Result packet `{}`. {}", packet.sourceAddress(), packet.destinationAddress(), resultString, result);
                results.add(result);
            }
        }
    }
}
