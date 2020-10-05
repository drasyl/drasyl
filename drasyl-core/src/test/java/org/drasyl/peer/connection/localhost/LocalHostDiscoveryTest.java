/*
 * Copyright (c) 2020.
 *
 * This file is part of drasyl.
 *
 *  drasyl is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  drasyl is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with drasyl.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.drasyl.peer.connection.localhost;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.crypto.CryptoException;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.peer.Endpoint;
import org.drasyl.peer.PeerInformation;
import org.drasyl.peer.PeersManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.anExistingFile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalHostDiscoveryTest {
    private final Duration leaseTime = ofSeconds(60);
    private final AtomicBoolean doScan = new AtomicBoolean();
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Path discoveryPath;
    @Mock
    private CompressedPublicKey ownPublicKey;
    @Mock
    private PeersManager peersManager;
    @Mock
    private Set<Endpoint> endpoints;
    @Mock
    private Observable<CompressedPublicKey> communicationOccurred;
    @Mock
    private Scheduler scheduler;
    private AtomicBoolean opened = new AtomicBoolean();
    @Mock
    private Disposable watchDisposable;
    @Mock
    private Disposable postDisposable;
    @Mock
    private Disposable communicationObserver;
    private LocalHostDiscovery underTest;

    @Nested
    class Open {
        @Test
        void shouldSetOpenToTrue() {
            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.open();

            assertTrue(opened.get());
        }

        @Test
        void shouldCreateDiscoveryDirectory() {
            when(discoveryPath.toFile().exists()).thenReturn(false);

            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.open();

            verify(discoveryPath.toFile()).mkdir();
        }

        @Test
        void shouldTryToRegisterAWatchService() throws IOException {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);

            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.open();

            verify(discoveryPath).register(any(), eq(ENTRY_CREATE), eq(ENTRY_MODIFY), eq(ENTRY_DELETE));
        }

        @Test
        void shouldScheduleTasksForPollingWatchServiceAndPostingOwnInformation() {
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);

            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.open();

            verify(scheduler).schedulePeriodicallyDirect(any(), eq(0L), eq(5L), eq(SECONDS));
            verify(scheduler).schedulePeriodicallyDirect(any(), eq(0L), eq(ofSeconds(55L).toMillis()), eq(MILLISECONDS));
        }

        @Test
        void scheduledTasksShouldPollWatchServiceAndPostOwnInformationToFileSystem(@TempDir final Path dir) throws IOException {
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            when(discoveryPath.toFile().exists()).thenReturn(true);
            when(discoveryPath.toFile().isDirectory()).thenReturn(true);
            when(discoveryPath.toFile().canRead()).thenReturn(true);
            when(discoveryPath.toFile().canWrite()).thenReturn(true);
            when(discoveryPath.resolve(anyString())).thenReturn(path);
            when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(5L), eq(SECONDS))).then(invocationOnMock -> {
                invocationOnMock.getArgument(0, Runnable.class).run();
                return null;
            });
            when(scheduler.schedulePeriodicallyDirect(any(), eq(0L), eq(ofSeconds(55L).toMillis()), eq(MILLISECONDS))).then(invocationOnMock -> {
                invocationOnMock.getArgument(0, Runnable.class).run();
                return null;
            });

            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, Set.of(Endpoint.of("ws://localhost:123")), communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.open();

            verify(discoveryPath.getFileSystem().newWatchService()).poll();
            assertThat(path.toFile(), anExistingFile());
        }
    }

    @Nested
    class Close {
        @Test
        void shouldSetOpenToFalse() {
            opened = new AtomicBoolean(true);
            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);

            underTest.close();

            assertFalse(opened.get());
        }

        @Test
        void shouldDisposeAllTasksAndSubscriptions() {
            opened = new AtomicBoolean(true);
            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, endpoints, communicationOccurred, opened, doScan, scheduler, watchDisposable, postDisposable, communicationObserver);

            underTest.close();

            verify(communicationObserver).dispose();
            verify(watchDisposable).dispose();
            verify(postDisposable).dispose();
        }
    }

    @Nested
    class Scan {
        @Test
        void shouldScanDirectory(@TempDir final Path dir) throws IOException, CryptoException {
            when(discoveryPath.toFile()).thenReturn(dir.toFile());
            final Path path = Paths.get(dir.toString(), "03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a.json");
            Files.writeString(path, "{\"endpoints\":[\"ws://localhost:123\"]}", StandardOpenOption.CREATE);

            underTest = new LocalHostDiscovery(discoveryPath, leaseTime, ownPublicKey, peersManager, Set.of(Endpoint.of("ws://localhost:123")), communicationOccurred, new AtomicBoolean(true), new AtomicBoolean(true), scheduler, watchDisposable, postDisposable, communicationObserver);
            underTest.scan();

            verify(peersManager).setPeerInformation(CompressedPublicKey.of("03409386a22294ee55393eb0f83483c54f847f700df687668cc8aa3caa19a9df7a"), PeerInformation.of(Set.of(Endpoint.of("ws://localhost:123"))));
        }
    }
}