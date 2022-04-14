package org.drasyl.cli.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.channel.DrasylServerChannel;
import org.drasyl.identity.IdentityPublicKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SpawnChildChannelToPeerTest {
    @Test
    void shouldSpawnNewChildChannelOnChannelActive(@Mock final DrasylServerChannel ch,
                                                   @Mock final IdentityPublicKey remoteAddress) {
        final ChannelHandler handler = new SpawnChildChannelToPeer(ch, remoteAddress);
        final EmbeddedChannel channel = new EmbeddedChannel(handler);

        assertEquals(new DrasylChannel(ch, remoteAddress).remoteAddress(), ((DrasylChannel) channel.readInbound()).remoteAddress());
    }

    @Test
    void shouldSpawnNewChildChannelOnHandlerAdded(@Mock final DrasylServerChannel ch,
                                                  @Mock final IdentityPublicKey remoteAddress) {
        final ChannelHandler handler = new SpawnChildChannelToPeer(ch, remoteAddress);
        final EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(handler);

        assertEquals(new DrasylChannel(ch, remoteAddress).remoteAddress(), ((DrasylChannel) channel.readInbound()).remoteAddress());
    }
}
