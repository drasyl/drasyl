package org.drasyl.cli.sdon.channel;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.internal.StringUtil;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.util.logging.Logger;
import org.drasyl.util.logging.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.*;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

class SdonFileNotifierHandler extends ChannelInboundHandlerAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(SdonFileNotifierHandler.class);

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws IOException {
        cleanupFiles(ctx);

        ctx.fireChannelInactive();
    }

    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws IOException {
        if (evt instanceof UdpServerBound) {
            createFile(ctx, evt);
        }

        ctx.fireUserEventTriggered(evt);
    }

    private static void createFile(final ChannelHandlerContext ctx, final Object evt) throws IOException {
        final SocketAddress localAddress = ctx.channel().localAddress();
        if (localAddress != null) {
            final File file = new File("drasyl-ipc/" + localAddress.toString() + "." + StringUtil.simpleClassName(evt));
            if (file.createNewFile()) {
                LOG.debug("Created `{}`.", file.getAbsolutePath());
            } else {
                LOG.error("Unable to create `{}`.", file.getAbsolutePath());
            }
        }
    }

    private static void cleanupFiles(final ChannelHandlerContext ctx) throws IOException {


        final Path dir = Paths.get("drasyl-ipc/");
        final SocketAddress localAddress = ctx.channel().localAddress();
        if (localAddress != null) {
            try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir, localAddress + ".*")) {
                for (final Path entry : stream) {
                    if (Files.isRegularFile(entry, NOFOLLOW_LINKS)) {
                        Files.delete(entry);
                        LOG.debug("Deleted `{}`.", entry);
                    }
                }
            }
        }
    }
}
