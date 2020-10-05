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

package org.drasyl.util;

import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortType;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;
import org.drasyl.util.PortMappingUtil.PortMapping;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.util.Optional;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortMappingUtilTest {
    @Nested
    class TestPortMapping {
        @Mock(answer = RETURNS_DEEP_STUBS)
        com.offbynull.portmapper.mapper.PortMapper mapper;
        InetSocketAddress address = InetSocketAddress.createUnresolved("192.168.188.112", 22527);
        @Mock
        Subject<Optional<InetSocketAddress>> externalAddressObservable;
        @Mock
        Scheduler scheduler;
        @Mock(answer = RETURNS_DEEP_STUBS)
        MappedPort mappedPort;
        @Mock
        Disposable refreshDisposable;
        private PortMapping underTest;

        @Nested
        class Constructor {
            @Test
            void shouldCreateMappingAndScheduleRefresh() throws InterruptedException {
                when(mapper.mapPort(PortType.TCP, 22527, 22527, 300)).thenReturn(mappedPort);
                when(mappedPort.getExternalAddress().getHostName()).thenReturn("1.2.3.4");
                when(mappedPort.getLifetime()).thenReturn(300L);
                when(scheduler.scheduleDirect(any(), eq(150_000L), eq(MILLISECONDS))).then(invocation -> {
                    final Runnable run = invocation.getArgument(0, Runnable.class);
                    run.run();
                    return null;
                }).thenReturn(refreshDisposable);

                underTest = new PortMapping(mapper, address, externalAddressObservable, scheduler);

                verify(mapper, times(2)).mapPort(PortType.TCP, 22527, 22527, 300);
                verify(scheduler, times(2)).scheduleDirect(any(), eq(150_000L), eq(MILLISECONDS));
            }
        }

        @Nested
        class Close {
            @Test
            void shouldCloseMapping() throws InterruptedException {
                externalAddressObservable = BehaviorSubject.createDefault(Optional.empty());

                underTest = new PortMapping(mapper, address, externalAddressObservable, scheduler, mappedPort, refreshDisposable);
                underTest.close();

                verify(mapper).unmapPort(mappedPort);
                verify(refreshDisposable).dispose();
            }
        }
    }
}