package org.drasyl.cli.sdo.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

class SdoFileNotifiedHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdoFileNotifiedHandler.class);

    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        final Path path = Paths.get("drasyl-ipc", "all.passChannelActive");

        if (Files.exists(path)) {
            LOG.info("File `{}` exist. Start Node.", path);
            ctx.fireChannelActive();

            checkCloseFile(ctx);
        }
        else {
            LOG.info("File does not `{}` exist. Wait...", path);

            ctx.executor().schedule(() -> channelActive(ctx), 1000, MILLISECONDS);
        }
    }

    private static void checkCloseFile(ChannelHandlerContext ctx) {
        ctx.executor().schedule(() -> {
            final Path path = Paths.get("drasyl-ipc", "all.passClose");

            if (Files.exists(path)) {
                LOG.info("File `{}` exist. Stop Node.", path);
                ctx.channel().close();
            }
            else {
                checkCloseFile(ctx);
            }
        }, 1000, MILLISECONDS);
    }
}
