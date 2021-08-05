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
package org.drasyl.localhost;

import io.netty.util.concurrent.Future;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.channel.EmbeddedDrasylServerChannel;
import org.drasyl.channel.MigrationHandlerContext;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteMessage;
import org.drasyl.util.ThrowingBiConsumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    @Mock(answer = RETURNS_DEEP_STUBS)
    private DrasylConfig config;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Identity identity;
    @Mock
    private PeersManager peersManager;
    private final Duration leaseTime = ofSeconds(60);
    @Mock(answer = RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private IdentityPublicKey ownPublicKey;
    @Mock
    private ThrowingBiConsumer<File, Object, IOException> jacksonWriter;
    private final Map<IdentityPublicKey, InetSocketAddressWrapper> routes = new HashMap<>();
    @Mock
    private Future watchDisposable;
    @Mock
    private Future postDisposable;

    @Nested
    class StartDiscovery {
        @SuppressWarnings("unchecked")
        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void shouldStartDiscoveryOnNodeUpEvent(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx) {
            when(ctx.dependentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.independentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.passEvent(any(), any())).then(invocation -> {
                final CompletableFuture<Void> future = invocation.getArgument(1, CompletableFuture.class);
                future.complete(null);
                return future;
            });
            when(ctx.config().getRemoteLocalHostDiscoveryPath().resolve(anyString()).toFile().mkdirs()).thenReturn(true);
            when(ctx.config().getRemoteLocalHostDiscoveryPath().resolve(anyString()).toFile().isDirectory()).thenReturn(true);
            when(ctx.config().getRemoteLocalHostDiscoveryPath().resolve(anyString()).toFile().canRead()).thenReturn(true);
            when(ctx.config().getRemoteLocalHostDiscoveryPath().resolve(anyString()).toFile().canWrite()).thenReturn(true);
            when(ctx.config().isRemoteLocalHostDiscoveryWatchEnabled()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);

            final CompletableFuture<Void> future = new CompletableFuture<>();
            handler.onEvent(ctx, event, future);
            future.join();

            verify(ctx.dependentScheduler()).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);
            when(config.isRemoteLocalHostDiscoveryWatchEnabled()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @SuppressWarnings("unchecked")
        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx) {
            when(ctx.dependentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.independentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.passEvent(any(), any())).then(invocation -> {
                final CompletableFuture<Void> future = invocation.getArgument(1, CompletableFuture.class);
                future.complete(null);
                return future;
            });
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(ctx.config().getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(ctx.config().getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);
            when(ctx.config().isRemoteLocalHostDiscoveryWatchEnabled()).thenReturn(true);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);

            final CompletableFuture<Void> future = new CompletableFuture<>();
            handler.onEvent(ctx, event, future);
            future.join();

            verify(ctx.dependentScheduler()).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
            verify(ctx.dependentScheduler()).schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS));
        }

        @SuppressWarnings("unchecked")
        @Test
        @Timeout(value = 5_000, unit = MILLISECONDS)
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@TempDir final Path dir,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final FileSystem fileSystem,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final WatchService watchService,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx) throws IOException {
            final Path path = Paths.get(dir.toString(), "18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127.json");
            final File file = discoveryPath.toFile(); // mockito work-around for an issue from 2015 (#330)

            doReturn(true).when(file).exists();
            doReturn(true).when(file).isDirectory();
            doReturn(true).when(file).canRead();
            doReturn(true).when(file).canWrite();
            doReturn(path).when(discoveryPath).resolve(anyString());
            when(ctx.dependentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.independentScheduler().scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.dependentScheduler().schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.dependentScheduler().schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(ctx.passEvent(any(), any())).then(invocation -> {
                final CompletableFuture<Void> future = invocation.getArgument(1, CompletableFuture.class);
                future.complete(null);
                return future;
            });

            final DrasylConfig config = ctx.config(); // mockito work-around
            final Path path2 = config.getRemoteLocalHostDiscoveryPath();

            doReturn(leaseTime).when(config).getRemoteLocalHostDiscoveryLeaseTime();
            doReturn(true).when(config).isRemoteLocalHostDiscoveryWatchEnabled();
            doReturn(discoveryPath).when(path2).resolve(anyString());
            doReturn(fileSystem).when(discoveryPath).getFileSystem();
            doReturn(watchService).when(fileSystem).newWatchService();

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);

            final CompletableFuture<Void> future = new CompletableFuture<>();
            handler.onEvent(ctx, event, future);
            future.join();

            verify(watchService).poll();
            verify(jacksonWriter).accept(eq(path.toFile()), any());
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final IdentityPublicKey publicKey,
                                                              @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(watchDisposable).cancel(false);
                verify(postDisposable).cancel(false);
                assertTrue(routes.isEmpty());
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final IdentityPublicKey publicKey,
                                                @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                pipeline.processInbound(event).join();

                verify(watchDisposable).cancel(false);
                verify(postDisposable).cancel(false);
                assertTrue(routes.isEmpty());
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class MessagePassing {
        @Test
        void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {
            final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
            final IdentityPublicKey recipient = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
            routes.put(recipient, address);
            when(identity.getIdentityPublicKey()).thenReturn(IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<RemoteMessage> outboundMessages = pipeline.drasylOutboundMessages(RemoteMessage.class).test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
            finally {
                pipeline.drasylClose();
            }
        }

        @Test
        void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final IdentityPublicKey recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteMessage message) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            final EmbeddedDrasylServerChannel pipeline = new EmbeddedDrasylServerChannel(config, identity, peersManager, handler);
            try {
                final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(null, recipient, message));
            }
            finally {
                pipeline.drasylClose();
            }
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final MigrationHandlerContext ctx,
                                 @Mock final InetSocketAddressWrapper address) throws IOException {
            final IdentityPublicKey publicKey = IdentityPublicKey.of("18cdb282be8d1293f5040cd620a91aca86a475682e4ddc397deabe300aad9127");
            routes.put(publicKey, address);

            when(ctx.config().getRemoteLocalHostDiscoveryPath()).thenReturn(dir);
            when(ctx.config().getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(ofMinutes(5));
            final Path path = Paths.get(dir.toString(), "0", "02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b.json");
            Files.createDirectory(path.getParent());
            Files.writeString(path, "[\"192.168.188.42:12345\",\"192.168.188.23:12345\"]", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            handler.scan(ctx);

            assertEquals(Map.of(
                    IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b"),
                    new InetSocketAddressWrapper("192.168.188.23", 12345)
            ), routes);

            verify(ctx.peersManager()).addPath(any(), eq(IdentityPublicKey.of("02bfa672181ef9c0a359dc68cc3a4d34f47752c8886a0c5661dc253ff5949f1b")), any());
        }
    }
}
