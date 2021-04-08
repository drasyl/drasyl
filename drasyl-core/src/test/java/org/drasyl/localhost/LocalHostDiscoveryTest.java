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

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import org.drasyl.DrasylConfig;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.identity.Identity;
import org.drasyl.identity.ProofOfWork;
import org.drasyl.peer.PeersManager;
import org.drasyl.pipeline.EmbeddedPipeline;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.pipeline.address.Address;
import org.drasyl.pipeline.address.InetSocketAddressWrapper;
import org.drasyl.pipeline.message.AddressedEnvelope;
import org.drasyl.pipeline.message.DefaultAddressedEnvelope;
import org.drasyl.remote.protocol.RemoteEnvelope;
import org.drasyl.util.ThrowingBiConsumer;
import org.drasyl.util.scheduler.DrasylScheduler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
    private CompressedPublicKey ownPublicKey;
    @Mock
    private ThrowingBiConsumer<File, Object, IOException> jacksonWriter;
    private final Map<CompressedPublicKey, InetSocketAddressWrapper> routes = new HashMap<>();
    @Mock
    private Disposable watchDisposable;
    @Mock
    private Disposable postDisposable;

    @Nested
    class StartDiscovery {
        @Test
        void shouldStartDiscoveryOnNodeUpEvent(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) {
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(dependentScheduler).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
            }
        }

        @Test
        void shouldTryToRegisterAWatchService(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event) throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
            }
        }

        @Test
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation(@Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                                                               @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) {
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(dependentScheduler).schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS));
                verify(dependentScheduler).schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS));
            }
        }

        @Test
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@TempDir final Path dir,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final FileSystem fileSystem,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final WatchService watchService,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler dependentScheduler,
                                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final DrasylScheduler independentScheduler) throws IOException {
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(path);
            when(dependentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(independentScheduler.scheduleDirect(any())).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(dependentScheduler.schedulePeriodicallyDirect(any(), anyLong(), eq(5_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(dependentScheduler.schedulePeriodicallyDirect(any(), anyLong(), eq(55_000L), eq(MILLISECONDS))).then(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            });
            when(config.getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(leaseTime);
            when(identity.getPublicKey()).thenReturn(ownPublicKey);
            when(config.getRemoteLocalHostDiscoveryPath().resolve(any(String.class))).thenReturn(discoveryPath);
            when(discoveryPath.getFileSystem()).thenReturn(fileSystem);
            when(fileSystem.newWatchService()).thenReturn(watchService);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, dependentScheduler, independentScheduler, handler)) {
                pipeline.processInbound(event).join();

                verify(watchService).poll();
                verify(jacksonWriter).accept(eq(path.toFile()), any());
            }
        }
    }

    @Nested
    class StopDiscovery {
        @Test
        void shouldStopDiscoveryOnNodeUnrecoverableErrorEvent(@Mock final NodeUnrecoverableErrorEvent event,
                                                              @Mock final CompressedPublicKey publicKey,
                                                              @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(watchDisposable).dispose();
                verify(postDisposable).dispose();
                assertTrue(routes.isEmpty());
            }
        }

        @Test
        void shouldStopDiscoveryOnNodeDownEvent(@Mock final NodeDownEvent event,
                                                @Mock final CompressedPublicKey publicKey,
                                                @Mock final InetSocketAddressWrapper address) {
            routes.put(publicKey, address);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                pipeline.processInbound(event).join();

                verify(watchDisposable).dispose();
                verify(postDisposable).dispose();
                assertTrue(routes.isEmpty());
            }
        }
    }

    @Nested
    class MessagePassing {
        @SuppressWarnings("rawtypes")
        @Test
        void shouldRouteOutboundMessageWhenStaticRouteIsPresent(@Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message) {
            final InetSocketAddressWrapper address = new InetSocketAddressWrapper(22527);
            final CompressedPublicKey recipient = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            routes.put(recipient, address);
            when(identity.getPublicKey()).thenReturn(CompressedPublicKey.of("0364417e6f350d924b254deb44c0a6dce726876822c44c28ce221a777320041458"));
            when(identity.getProofOfWork()).thenReturn(ProofOfWork.of(1));

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<RemoteEnvelope> outboundMessages = pipeline.outboundMessages(RemoteEnvelope.class).test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1);
            }
        }

        @SuppressWarnings("rawtypes")
        @Test
        void shouldPassthroughMessageWhenStaticRouteIsAbsent(@Mock final CompressedPublicKey recipient,
                                                             @Mock(answer = RETURNS_DEEP_STUBS) final RemoteEnvelope message) {
            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            try (final EmbeddedPipeline pipeline = new EmbeddedPipeline(config, identity, peersManager, handler)) {
                final TestObserver<AddressedEnvelope<Address, Object>> outboundMessages = pipeline.outboundMessagesWithRecipient().test();

                pipeline.processOutbound(recipient, message).join();

                outboundMessages.awaitCount(1)
                        .assertValueCount(1)
                        .assertValue(new DefaultAddressedEnvelope<>(null, recipient, message));
            }
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir,
                                 @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                 @Mock final InetSocketAddressWrapper address) throws IOException {
            final CompressedPublicKey publicKey = CompressedPublicKey.of("030944d202ce5ff0ee6df01482d224ccbec72465addc8e4578edeeaa5997f511bb");
            routes.put(publicKey, address);

            when(ctx.config().getRemoteLocalHostDiscoveryPath()).thenReturn(dir);
            when(ctx.config().getRemoteLocalHostDiscoveryLeaseTime()).thenReturn(ofMinutes(5));
            final Path path = Paths.get(dir.toString(), "0", "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            Files.createDirectory(path.getParent());
            Files.writeString(path, "[\"192.168.188.42:12345\",\"192.168.188.23:12345\"]", StandardOpenOption.CREATE);

            final LocalHostDiscovery handler = new LocalHostDiscovery(jacksonWriter, routes, watchDisposable, postDisposable);
            handler.scan(ctx);

            assertEquals(Map.of(
                    CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"),
                    new InetSocketAddressWrapper("192.168.188.23", 12345)
            ), routes);

            verify(ctx.peersManager()).addPath(eq(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a")), any());
        }
    }
}
