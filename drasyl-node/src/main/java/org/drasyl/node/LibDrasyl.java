/*
 * Copyright (c) 2020-2022 Heiko Bornholdt and Kevin Röbert
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
package org.drasyl.node;

import io.netty.util.concurrent.Future;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.util.Map;
import java.util.function.Consumer;

public class LibDrasyl {
    private static DrasylNode node;
    private static boolean online;
    private static Consumer<Event> eventConsumer;

    @CEntryPoint(name = "filter_env")
    private static int filterEnv(final IsolateThread thread, final CCharPointer cFilter) {
        String filter = CTypeConversion.toJavaString(cFilter);
        Map<String, String> env = System.getenv();
        int count = 0;
        for (String envName : env.keySet()) {
            if (!envName.contains(filter)) {
                continue;
            }
            System.out.format("%s=%s%n",
                    envName,
                    env.get(envName));
            count++;
        }
        return count;
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_start")
    private static int nodeStart(final IsolateThread thread) {
        if (node != null) {
            return 1;
        }

        try {
            node = new DrasylNode() {
                @Override
                public void onEvent(final Event event) {
                    if (event instanceof NodeOnlineEvent) {
                        online = true;
                    }
                    else if (event instanceof NodeOfflineEvent) {
                        online = false;
                    }
//                    if (eventConsumer != null) {
//                        eventConsumer.accept(event);
//                    }
                }
            };
            node.start().toCompletableFuture().join();
            return 0;
        }
        catch (final Exception e) {
            return 1;
        }
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_stop")
    private static int nodeStop(final IsolateThread thread) {
        if (node == null) {
            System.out.println("läuft gar nicht");
            return 1;
        }

        try {
            node.shutdown().toCompletableFuture().join();
            return 0;
        }
        catch (final Exception e) {
            e.printStackTrace();
            return 2;
        }
    }

    @SuppressWarnings({ "java:S109", "java:S1166", "java:S2221", "java:S2142", "BusyWait" })
    @CEntryPoint(name = "drasyl_shutdown_event_loop")
    private static int shutdownEventLoop(final IsolateThread thread) {
        try {
            final Future<Void> future = DrasylNodeSharedEventLoopGroupHolder.shutdown();
            while (!future.isDone()) {
                Thread.sleep(50);
            }
            if (!future.isSuccess()) {
                return 1;
            }
            return 0;
        }
        catch (final Exception e) {
            return 1;
        }
    }
//
//    @CEntryPoint(name = "drasyl_node_set_event_handler")
//    private static int nodeSetEventHandler(final IsolateThread thread,
//                                           final DrasylNodeEventListener pointer) {
//        try {
//            eventConsumer = event -> {
//                final int eventCode;
//                // node events
//                if (event instanceof NodeUpEvent) {
//                    eventCode = 100;
//                }
//                else if (event instanceof NodeDownEvent) {
//                    eventCode = 101;
//                }
//                else if (event instanceof NodeOnlineEvent) {
//                    eventCode = 102;
//                }
//                else if (event instanceof NodeOfflineEvent) {
//                    eventCode = 103;
//                }
//                else if (event instanceof NodeUnrecoverableErrorEvent) {
//                    eventCode = 104;
//                }
//                else if (event instanceof NodeNormalTerminationEvent) {
//                    eventCode = 105;
//                }
//                // peer events
//                else if (event instanceof PeerDirectEvent) {
//                    eventCode = 200;
//                }
//                else if (event instanceof PeerRelayEvent) {
//                    eventCode = 201;
//                }
//                else if (event instanceof LongTimeEncryptionEvent) {
//                    eventCode = 202;
//                }
//                else if (event instanceof PerfectForwardSecrecyEncryptionEvent) {
//                    eventCode = 203;
//                }
//                // message events
//                else if (event instanceof MessageEvent) {
//                    eventCode = 300;
//                }
//                else if (event instanceof InboundExceptionEvent) {
//                    eventCode = 400;
//                }
//                else {
//                    eventCode = 0;
//                }
//                pointer.invoke(thread, eventCode);
//            };
//            return 0;
//        }
//        catch (final Exception e) {
//            return 1;
//        }
//    }
//
//    public interface DrasylNodeEventListener extends CFunctionPointer {
//        @InvokeCFunctionPointer
//        int invoke(IsolateThread thread, int eventCode);
//    }
//
//    @CEntryPoint(name = "drasyl_node_send")
//    private static int nodeSend(final IsolateThread thread,
//                                final CCharPointer recipientPointer,
//                                final CCharPointer payloadPointer) {
//        try {
//            final String recipient = CTypeConversion.toJavaString(recipientPointer);
//            final String payload = CTypeConversion.toJavaString(payloadPointer);
//            node.send(recipient, payload).toCompletableFuture().join();
//            return 0;
//        }
//        catch (final Exception e) {
//            return 1;
//        }
//    }
//

//
//    @CEntryPoint(name = "drasyl_node_is_online")
//    private static int nodeIsOnline(final IsolateThread thread) {
//        if (online) {
//            return 1;
//        }
//        else {
//            return 0;
//        }
//    }
//
//    @CEntryPoint(name = "drasyl_util_delay")
//    private static void utilDelay(final IsolateThread thread, final long millis) {
//        try {
//            Thread.sleep(millis);
//        }
//        catch (final InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
