/*
 * Copyright (c) 2020-2021 Heiko Bornholdt and Kevin Röbert
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.drasyl.handler.remote;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.DrasylServerChannelConfig;
import org.drasyl.channel.embedded.UserEventAwareEmbeddedChannel;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.remote.UdpServer.UdpServerBound;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.ThrowingFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.drasyl.handler.remote.LocalHostDiscovery.PATH_ID;
import static org.drasyl.handler.remote.LocalHostDiscovery.PATH_PRIORITY;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylServerChannelConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    private final Duration leaseTime = ofSeconds(60);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private ThrowingFunction<File, Set<InetSocketAddress>, IOException> fileReader;
    @Mock
    private ThrowingBiConsumer<File, Set<InetSocketAddress>, IOException> fileWriter;
    @Mock
    private Future<?> watchDisposable;
    @Mock
    private Future<?> postDisposable;
    @SuppressWarnings("unused")
    private boolean watchEnabled;

    @Nested
    class StartDiscovery {
        @Test
        @Timeout(value = 10_000, unit = MILLISECONDS)
        void shouldStartDiscoveryOnPortEvent(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServerBound event,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().config()).thenReturn(config);
            when(event.getBindAddress()).thenReturn(new InetSocketAddress(12345));
            when(discoveryPath.resolve(anyString()).toFile().mkdirs()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().canRead()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().canWrite()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, false, leaseTime, discoveryPath, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(ctx.executor()).execute(any());
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServerBound event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, true, leaseTime, discoveryPath, watchDisposable, postDisposable);
            final EmbeddedChannel channel = new UserEventAwareEmbeddedChannel(config, identity.getAddress(), handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            }
            finally {
                channel.close();
            }
        }

        @Test
        @Timeout(value = 10_000, unit = MILLISECONDS)
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServerBound event,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
            when(ctx.channel().config()).thenReturn(config);
            when(event.getBindAddress()).thenReturn(new InetSocketAddress(12345));
            when(ctx.executor()).thenReturn(executor);
            doAnswer(invocation1 -> {
                invocation1.getArgument(0, Runnable.class).run();
                return null;
            }).when(executor).execute(any());
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, true, leaseTime, discoveryPath, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(ctx.executor()).scheduleWithFixedDelay(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
            verify(ctx.executor()).scheduleWithFixedDelay(any(), anyLong(), eq(55_000L), eq(MILLISECONDS));
        }

        @Test
        @Timeout(value = 10_000, unit = MILLISECONDS)
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServerBound event,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final FileSystem fileSystem,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final WatchService watchService,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) throws IOException {
            when(ctx.channel().config()).thenReturn(config);
            when(event.getBindAddress()).thenReturn(new InetSocketAddress(12345));
            when(ctx.executor()).thenReturn(executor);
            final File file = discoveryPath.toFile(); // mockito work-around for an issue from 2015 (#330)

            doReturn(true).when(file).exists();
            doReturn(true).when(file).isDirectory();
            doReturn(true).when(file).canRead();
            doReturn(true).when(file).canWrite();
            doAnswer(invocation1 -> {
                invocation1.getArgument(0, Runnable.class).run();
                return null;
            }).when(executor).execute(any());
            when((Future<?>) ctx.executor().scheduleWithFixedDelay(any(), anyLong(), eq(5_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when((Future<?>) ctx.executor().scheduleWithFixedDelay(any(), anyLong(), eq(55_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });

            doReturn(discoveryPath).when(discoveryPath).resolve(anyString());
            doReturn(fileSystem).when(discoveryPath).getFileSystem();
            doReturn(watchService).when(fileSystem).newWatchService();

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, true, leaseTime, discoveryPath, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(watchService).poll();
            verify(fileWriter).accept(any(), any());
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnChannelInactive(@Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(ctx.channel().config()).thenReturn(config);
            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, watchEnabled, leaseTime, discoveryPath, watchDisposable, postDisposable);
            handler.channelInactive(ctx);

            verify(watchDisposable).cancel(false);
            verify(postDisposable).cancel(false);
            verify(config.getPeersManager()).removePaths(PATH_ID);
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) throws IOException {
            when(ctx.channel().config()).thenReturn(config);
            when(fileReader.apply(any())).thenReturn(Set.of(new InetSocketAddress("192.168.188.23", 12345)));
            when(config.getPeersManager().addPath(any(), any(), any(), anyShort())).thenReturn(true);

            final Path path = Paths.get(dir.toString(), "0", "02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b.txt");
            Files.createDirectory(path.getParent());
            Files.writeString(path, "192.168.188.42:12345\n192.168.188.23:12345", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, watchEnabled, ofMinutes(5), dir, watchDisposable, postDisposable);
            handler.scan(ctx);

            verify(config.getPeersManager()).addPath(IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"), PATH_ID, new InetSocketAddress("192.168.188.23", 12345), PATH_PRIORITY);

            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }
    }
}
