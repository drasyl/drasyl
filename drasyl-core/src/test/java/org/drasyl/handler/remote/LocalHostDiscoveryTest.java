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
import io.netty.util.ReferenceCounted;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import org.drasyl.channel.AddressedMessage;
import org.drasyl.handler.discovery.AddPathEvent;
import org.drasyl.handler.remote.protocol.RemoteMessage;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    private final Duration leaseTime = ofSeconds(60);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private IdentityPublicKey ownPublicKey;
    @Mock
    private ThrowingFunction<File, Set<InetSocketAddress>, IOException> fileReader;
    @Mock
    private ThrowingBiConsumer<File, Set<InetSocketAddress>, IOException> fileWriter;
    private final Map<IdentityPublicKey, SocketAddress> routes = new HashMap<>();
    @Mock
    private Future<?> watchDisposable;
    @Mock
    private Future<?> postDisposable;
    private boolean watchEnabled;
    @Mock
    private InetAddress bindHost;
    private int networkId;

    @Nested
    class StartDiscovery {
        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void shouldStartDiscoveryOnPortEvent(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServer.Port event,
                                             @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx) {
            when(discoveryPath.resolve(anyString()).toFile().mkdirs()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().canRead()).thenReturn(true);
            when(discoveryPath.resolve(anyString()).toFile().canWrite()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), false, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(ctx.executor()).execute(any());
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServer.Port event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), true, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.pipeline().fireUserEventTriggered(event);

                verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            }
            finally {
                channel.close();
            }
        }

        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServer.Port event,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) {
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

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), true, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(ctx.executor()).scheduleWithFixedDelay(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
            verify(ctx.executor()).scheduleWithFixedDelay(any(), anyLong(), eq(55_000L), eq(MILLISECONDS));
        }

        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@Mock(answer = RETURNS_DEEP_STUBS) final UdpServer.Port event,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final FileSystem fileSystem,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final WatchService watchService,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final EventExecutor executor) throws IOException {
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

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), true, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);

            handler.userEventTriggered(ctx, event);

            verify(watchService).poll();
            verify(fileWriter).accept(any(), any());
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnChannelInactive(@Mock final IdentityPublicKey publicKey,
                                                  @Mock final SocketAddress address,
                                                  @Mock final ChannelHandlerContext ctx) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), watchEnabled, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);
            handler.channelInactive(ctx);

            verify(watchDisposable).cancel(false);
            verify(postDisposable).cancel(false);
            assertTrue(routes.isEmpty());
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {
            final SocketAddress address = new InetSocketAddress(22527);
            final IdentityPublicKey recipient = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
            routes.put(recipient, address);
            when(identity.getIdentityPublicKey()).thenReturn(IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), watchEnabled, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new AddressedMessage<>(message, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertNotNull(actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }

        @Test
        void shouldPassThroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), watchEnabled, bindHost, leaseTime, discoveryPath, networkId, watchDisposable, postDisposable);
            final EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                channel.writeAndFlush(new AddressedMessage<>(message, recipient));

                final ReferenceCounted actual = channel.readOutbound();
                assertEquals(new AddressedMessage<>(message, recipient), actual);

                actual.release();
            }
            finally {
                channel.close();
            }
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final ChannelHandlerContext ctx,
                                 @Mock final SocketAddress address) throws IOException {
            when(fileReader.apply(any())).thenReturn(Set.of(new InetSocketAddress("192.168.188.23", 12345)));

            final IdentityPublicKey publicKey = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
            routes.put(publicKey, address);

            final Path path = Paths.get(dir.toString(), "0", "02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b.txt");
            Files.createDirectory(path.getParent());
            Files.writeString(path, "192.168.188.42:12345\n192.168.188.23:12345", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(fileReader, fileWriter, routes, identity.getAddress(), watchEnabled, bindHost, ofMinutes(5), dir, networkId, watchDisposable, postDisposable);
            handler.scan(ctx);

            assertEquals(Map.of(
                    IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"),
                    new InetSocketAddress("192.168.188.23", 12345)
            ), routes);

            verify(ctx).fireUserEventTriggered(any(AddPathEvent.class));
        }
    }
}
