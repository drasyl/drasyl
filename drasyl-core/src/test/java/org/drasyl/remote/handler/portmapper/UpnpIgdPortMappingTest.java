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
package org.drasyl.remote.handler.portmapper;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.crypto.HexUtil;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.pipeline.HandlerContext;
import org.drasyl.util.protocol.UpnpIgdUtil;
import org.drasyl.util.protocol.UpnpIgdUtil.Service;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.drasyl.util.protocol.UpnpIgdUtil.SSDP_MULTICAST_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UpnpIgdPortMappingTest {
    @Nested
    class Start {
        @Test
        void shouldStartSsdpDiscovery(@Mock final UpnpIgdUtil upnpIgdUtil,
                                      @Mock final Set<URI> ssdpServices,
                                      @Mock final Disposable timeoutGuard,
                                      @Mock final Disposable ssdpDiscoverTask,
                                      @Mock final Disposable refreshTask,
                                      @Mock final Service upnpService,
                                      @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                      @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                      @Mock final Runnable onFailure) {
            new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

            verify(ctx).passOutbound(any(), any(), any());
        }

        @Nested
        class OnDiscoveryTimeout {
            @Test
            void shouldScheduleRefreshWhenMappingCouldBeCreated(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                                @Mock final Disposable timeoutGuard,
                                                                @Mock final Disposable ssdpDiscoverTask,
                                                                @Mock final Disposable refreshTask,
                                                                @Mock final Service upnpService,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                @Mock final Runnable onFailure) throws InterruptedException {
                final Set<URI> ssdpServices = new HashSet<>();
                when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                    final Runnable runnable = invocation.getArgument(0, Runnable.class);
                    ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                    runnable.run();
                    return null;
                });
                when(upnpIgdUtil.getStatusInfo(any(), any()).isConnected()).thenReturn(true);

                new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                verify(ctx.independentScheduler()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
            }

            @Test
            void shouldScheduleRefreshWhenThereIsAnExistingMapping(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                                   @Mock final Disposable timeoutGuard,
                                                                   @Mock final Disposable ssdpDiscoverTask,
                                                                   @Mock final Disposable refreshTask,
                                                                   @Mock final Service upnpService,
                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                                   @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                   @Mock final Runnable onFailure) throws InterruptedException {
                final Set<URI> ssdpServices = new HashSet<>();
                when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                    final Runnable runnable = invocation.getArgument(0, Runnable.class);
                    ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                    runnable.run();
                    return null;
                });
                when(upnpIgdUtil.getStatusInfo(any(), any()).isConnected()).thenReturn(true);
                when(upnpIgdUtil.getSpecificPortMappingEntry(any(), any(), any()).getDescription()).thenReturn("drasyl1234567890");
                when(event.getNode().getIdentity().getPublicKey().toString()).thenReturn("1234567890");

                new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                verify(ctx.independentScheduler()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
            }

            @Nested
            class WhenMappingFailed {
                @Test
                void shouldFailIfServiceDetailsCouldNotObtained(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                                @Mock final Disposable timeoutGuard,
                                                                @Mock final Disposable ssdpDiscoverTask,
                                                                @Mock final Disposable refreshTask,
                                                                @Mock final Service upnpService,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                                @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                @Mock final Runnable onFailure) throws InterruptedException {
                    final Set<URI> ssdpServices = new HashSet<>();
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                        final Runnable runnable = invocation.getArgument(0, Runnable.class);
                        ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                        runnable.run();
                        return null;
                    });
                    when(upnpIgdUtil.getUpnpService(any())).thenReturn(null);

                    new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                    verify(ctx.independentScheduler(), never()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
                }

                @Test
                void shouldFailIfStatusInfoCouldNotObtained(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                            @Mock final Disposable timeoutGuard,
                                                            @Mock final Disposable ssdpDiscoverTask,
                                                            @Mock final Disposable refreshTask,
                                                            @Mock final Service upnpService,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                            @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                            @Mock final Runnable onFailure) throws InterruptedException {
                    final Set<URI> ssdpServices = new HashSet<>();
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                        final Runnable runnable = invocation.getArgument(0, Runnable.class);
                        ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                        runnable.run();
                        return null;
                    });
                    when(upnpIgdUtil.getStatusInfo(any(), any())).thenReturn(null);

                    new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                    verify(ctx.independentScheduler(), never()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
                }

                @Test
                void shouldFailIfExternalAddressCouldNotObtained(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                                 @Mock final Disposable timeoutGuard,
                                                                 @Mock final Disposable ssdpDiscoverTask,
                                                                 @Mock final Disposable refreshTask,
                                                                 @Mock final Service upnpService,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                                 @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                                 @Mock final Runnable onFailure) throws InterruptedException {
                    final Set<URI> ssdpServices = new HashSet<>();
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                        final Runnable runnable = invocation.getArgument(0, Runnable.class);
                        ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                        runnable.run();
                        return null;
                    });
                    when(upnpIgdUtil.getStatusInfo(any(), any()).isConnected()).thenReturn(true);
                    when(upnpIgdUtil.getExternalIpAddress(any(), any())).thenReturn(null);

                    new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                    verify(ctx.independentScheduler(), never()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
                }

                @Test
                void shouldFailIfMappingRequestFailed(@Mock(answer = RETURNS_DEEP_STUBS) final UpnpIgdUtil upnpIgdUtil,
                                                      @Mock final Disposable timeoutGuard,
                                                      @Mock final Disposable ssdpDiscoverTask,
                                                      @Mock final Disposable refreshTask,
                                                      @Mock final Service upnpService,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx,
                                                      @Mock(answer = RETURNS_DEEP_STUBS) final NodeUpEvent event,
                                                      @Mock final Runnable onFailure) throws InterruptedException {
                    final Set<URI> ssdpServices = new HashSet<>();
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 10_000), eq(MILLISECONDS))).then(invocation -> null);
                    when(ctx.independentScheduler().scheduleDirect(any(), eq((long) 5_000), eq(MILLISECONDS))).then(invocation -> {
                        final Runnable runnable = invocation.getArgument(0, Runnable.class);
                        ssdpServices.add(URI.create("http://192.168.188.1:5000/rootDesc.xml"));
                        runnable.run();
                        return null;
                    });
                    when(upnpIgdUtil.getStatusInfo(any(), any()).isConnected()).thenReturn(true);
                    when(upnpIgdUtil.addPortMapping(any(), any(), any(), any(), any())).thenReturn(null);

                    new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).start(ctx, event, onFailure);

                    verify(ctx.independentScheduler(), never()).scheduleDirect(any(), eq((long) 300), eq(SECONDS));
                }
            }
        }
    }

    @Nested
    class Stop {
        @Test
        void shouldDestroyMapping(@Mock final UpnpIgdUtil upnpIgdUtil,
                                  @Mock final Set<URI> ssdpServices,
                                  @Mock final Disposable timeoutGuard,
                                  @Mock final Disposable ssdpDiscoverTask,
                                  @Mock final Disposable refreshTask,
                                  @Mock final Service upnpService,
                                  @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) throws InterruptedException {
            when(ctx.independentScheduler().scheduleDirect(any())).then(invocation -> {
                final Runnable runnable = invocation.getArgument(0, Runnable.class);
                runnable.run();
                return null;
            });

            new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, null).stop(ctx);

            verify(upnpIgdUtil).deletePortMapping(any(), any(), eq(0));
        }
    }

    @Nested
    class HandleMessage {
        @Test
        void shouldHandleIncomingSsdpPacket(@Mock final UpnpIgdUtil upnpIgdUtil,
                                            @Mock final Disposable timeoutGuard,
                                            @Mock final Disposable ssdpDiscoverTask,
                                            @Mock final Disposable refreshTask,
                                            @Mock final Service upnpService,
                                            @Mock final Runnable onFailure,
                                            @Mock(answer = RETURNS_DEEP_STUBS) final HandlerContext ctx) {
            final Set<URI> ssdpServices = new HashSet<>();
            final ByteBuf byteBuf = Unpooled.wrappedBuffer(HexUtil.fromString("485454502f312e3120323030204f4b0d0a43414348452d434f4e54524f4c3a206d61782d6167653d3132300d0a53543a2075726e3a736368656d61732d75706e702d6f72673a736572766963653a57414e436f6d6d6f6e496e74657266616365436f6e6669673a310d0a55534e3a20757569643a64623730643264372d333030312d343763632d626331622d6537316536636335623537343a3a75726e3a736368656d61732d75706e702d6f72673a736572766963653a57414e436f6d6d6f6e496e74657266616365436f6e6669673a310d0a4558543a0d0a5345525645523a20416d706c6946692f416d706c6946692f2055506e502f312e31204d696e6955506e50642f322e310d0a4c4f434154494f4e3a20687474703a2f2f3139322e3136382e3138382e313a353030302f726f6f74446573632e786d6c0d0a4f50543a2022687474703a2f2f736368656d61732e75706e702e6f72672f75706e702f312f302f223b206e733d30310d0a30312d4e4c533a20313630333337353039370d0a424f4f5449442e55504e502e4f52473a20313630333337353039370d0a434f4e46494749442e55504e502e4f52473a20313333370d0a0d0a"));
            new UpnpIgdPortMapping(new AtomicBoolean(true), upnpIgdUtil, ssdpServices, null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, onFailure).handleMessage(ctx, SSDP_MULTICAST_ADDRESS, byteBuf);

            assertEquals(1, ssdpServices.size());
        }
    }

    @Nested
    class Fail {
        @Test
        void shouldDisposeAllTasks(@Mock final UpnpIgdUtil upnpIgdUtil,
                                   @Mock final Disposable timeoutGuard,
                                   @Mock final Disposable ssdpDiscoverTask,
                                   @Mock final Disposable refreshTask,
                                   @Mock final Service upnpService,
                                   @Mock final Runnable onFailure) {
            new UpnpIgdPortMapping(new AtomicBoolean(), upnpIgdUtil, new HashSet<>(), null, 0, timeoutGuard, ssdpDiscoverTask, refreshTask, upnpService, onFailure).fail();

            verify(timeoutGuard).dispose();
            verify(refreshTask).dispose();
            verify(onFailure).run();
        }
    }
}
