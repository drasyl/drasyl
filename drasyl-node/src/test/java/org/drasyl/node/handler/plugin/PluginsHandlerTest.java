package org.drasyl.node.handler.plugin;

import io.netty.channel.ChannelHandlerContext;
import org.drasyl.identity.Identity;
import org.drasyl.node.DrasylConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginsHandlerTest {
    @Mock
    private DrasylConfig config;
    @Mock
    private Identity identity;
    @Mock
    private DrasylPlugin plugin;

    @BeforeEach
    void setUp() {
        when(config.getPlugins()).thenReturn(Set.of(plugin));
    }

    @Nested
    class ChannelRegistered {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelRegistered(ctx);

            verify(plugin).onBeforeStart(any());
        }
    }

    @Nested
    class ChannelActive {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            ctx.fireChannelActive();

            verify(plugin).onAfterStart(any());
        }
    }

    @Nested
    class ChannelInactive {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelInactive(ctx);

            verify(plugin).onBeforeShutdown(any());
        }
    }

    @Nested
    class ChannelUnregistered {
        @Test
        void test(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            final PluginsHandler handler = new PluginsHandler(config, identity);

            handler.channelUnregistered(ctx);

            verify(plugin).onAfterShutdown(any());
        }
    }
}
