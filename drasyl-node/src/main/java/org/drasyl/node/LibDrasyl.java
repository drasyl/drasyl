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
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.InboundExceptionEvent;
import org.drasyl.node.event.LongTimeEncryptionEvent;
import org.drasyl.node.event.MessageEvent;
import org.drasyl.node.event.NodeDownEvent;
import org.drasyl.node.event.NodeEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerEvent;
import org.drasyl.node.event.PeerRelayEvent;
import org.drasyl.node.event.PerfectForwardSecrecyEncryptionEvent;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

@CContext(LibDrasyl.Directives.class)
public class LibDrasyl {
    static {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.OFF);
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(Level.OFF);
        }
        System.out.println("level set: " + Level.OFF);
    }

    public static final int DRASYL_NODE_EVENT_NODE_UP = 100;
    public static final int DRASYL_NODE_EVENT_NODE_DOWN = 101;
    public static final int DRASYL_NODE_EVENT_NODE_ONLINE = 102;
    public static final int DRASYL_NODE_EVENT_NODE_OFFLINE = 103;
    public static final int DRASYL_NODE_EVENT_NODE_UNRECOVERABLE_ERROR = 104;
    public static final int DRASYL_NODE_EVENT_NODE_NORMAL_TERMINATION = 105;
    public static final int DRASYL_NODE_EVENT_PEER_DIRECT = 200;
    public static final int DRASYL_NODE_EVENT_PEER_RELAY = 201;
    public static final int DRASYL_NODE_EVENT_LONG_TIME_ENCRYPTION = 202;
    public static final int DRASYL_NODE_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 203;
    public static final int DRASYL_NODE_EVENT_MESSAGE = 300;
    public static final int DRASYL_NODE_EVENT_INBOUND_EXCEPTION = 400;
    private static DrasylNode node;
    private static boolean online;
    private static Consumer<Event> eventConsumer;

    public static final class Directives implements CContext.Directives {
        @Override
        public List<String> getOptions() {
            File[] jnis = findJNIHeaders();
            return Arrays.asList("-I" + jnis[0].getParent());
        }

        @Override
        public List<String> getHeaderFiles() {
            File[] jnis = findJNIHeaders();
            return Arrays.asList("<" + jnis[0] + ">");
        }

        private static File[] findJNIHeaders() throws IllegalStateException {
            return new File[]{
                    new File("/Users/heiko/Development/drasyl/test.h"),
                    };
        }
    }

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
                    if (eventConsumer != null) {
                        eventConsumer.accept(event);
                    }
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
            return 1;
        }

        try {
            node.shutdown().toCompletableFuture().join();
            node = null;
            return 0;
        }
        catch (final Exception e) {
            return 1;
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

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_set_event_handler")
    private static int nodeSetEventHandler(final IsolateThread thread,
                                           final DrasylNodeEventListener pointer) {
        try {
            eventConsumer = event -> {
                final DrasylNodeInfo drasylNodeInfo = StackValue.get(DrasylNodeInfo.class);
                final DrasylPeerInfo drasylPeerInfo = StackValue.get(DrasylPeerInfo.class);
                final DrasylNodeEvent drasylNodeEvent = StackValue.get(DrasylNodeEvent.class);

                final int eventCode;
                // node events
                if (event instanceof NodeUpEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_UP;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof NodeDownEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_DOWN;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof NodeOnlineEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_ONLINE;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof NodeOfflineEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_OFFLINE;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof NodeUnrecoverableErrorEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_UNRECOVERABLE_ERROR;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof NodeNormalTerminationEvent) {
                    eventCode = DRASYL_NODE_EVENT_NODE_NORMAL_TERMINATION;
                    CTypeConversion.toCString(((NodeEvent)event).getNode().getIdentity().getAddress().toString(), drasylNodeInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                // peer events
                else if (event instanceof PeerDirectEvent) {
                    eventCode = DRASYL_NODE_EVENT_PEER_DIRECT;
                    CTypeConversion.toCString(((PeerEvent)event).getPeer().getAddress().toString(), drasylPeerInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof PeerRelayEvent) {
                    eventCode = DRASYL_NODE_EVENT_PEER_RELAY;
                    CTypeConversion.toCString(((PeerEvent)event).getPeer().getAddress().toString(), drasylPeerInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof LongTimeEncryptionEvent) {
                    eventCode = DRASYL_NODE_EVENT_LONG_TIME_ENCRYPTION;
                    CTypeConversion.toCString(((PeerEvent)event).getPeer().getAddress().toString(), drasylPeerInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                else if (event instanceof PerfectForwardSecrecyEncryptionEvent) {
                    eventCode = DRASYL_NODE_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION;
                    CTypeConversion.toCString(((PeerEvent)event).getPeer().getAddress().toString(), drasylPeerInfo.getAddress(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                }
                // message events
                else if (event instanceof MessageEvent) {
                    eventCode = DRASYL_NODE_EVENT_MESSAGE;
                    CTypeConversion.toCString(((MessageEvent)event).getSender().toString(), drasylNodeEvent.getMessageSender(), WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING));
                    final String payload = ((MessageEvent) event).getPayload().toString();
                    drasylNodeEvent.setMessagePayloadLength(payload.length());
                    final CTypeConversion.CCharPointerHolder cCharPointerHolder = CTypeConversion.toCString(payload);
                    drasylNodeEvent.setMessagePayload(cCharPointerHolder.get());
                    //CTypeConversion.toCString(payload, drasylNodeEvent.getMessagePayload().read(), WordFactory.unsigned(payload.length()));
                }
                else if (event instanceof InboundExceptionEvent) {
                    eventCode = DRASYL_NODE_EVENT_INBOUND_EXCEPTION;
                }
                else {
                    eventCode = 0;
                }

                drasylNodeEvent.setNode(drasylNodeInfo);
                drasylNodeEvent.setPeer(drasylPeerInfo);
//                System.out.println("SizeOf " + SizeOf.get(DrasylNodeEvent.class));
//                System.out.println("eventCode " + eventCode);
//                System.out.println("drasylNodeEvent " + drasylNodeEvent);
                drasylNodeEvent.setEventCode(eventCode);

                pointer.invoke(thread, drasylNodeEvent);
            };
            return 0;
        }
        catch (final Exception e) {
            return 1;
        }
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_send")
    private static int nodeSend(final IsolateThread thread,
                                final CCharPointer recipientPointer,
                                final CCharPointer payloadPointer) {
        if (node == null) {
            return 1;
        }

        try {
            final String recipient = CTypeConversion.toJavaString(recipientPointer);
            final String payload = CTypeConversion.toJavaString(payloadPointer);
            node.send(recipient, payload).toCompletableFuture().join();
            return 0;
        }
        catch (final Exception e) {
            return 1;
        }
    }

    @CEntryPoint(name = "drasyl_node_is_online")
    private static int nodeIsOnline(final IsolateThread thread) {
        if (online) {
            return 1;
        }
        else {
            return 0;
        }
    }

    @SuppressWarnings({ "java:S112", "java:S2142" })
    @CEntryPoint(name = "drasyl_util_delay")
    private static void utilDelay(final IsolateThread thread, final long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public interface DrasylNodeEventListener extends CFunctionPointer {
        @SuppressWarnings("UnusedReturnValue")
        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, DrasylNodeEvent event);
    }

    @CStruct(value = "drasyl_node")
    public interface DrasylNodeInfo extends PointerBase {
        @CFieldAddress("address")
        CCharPointer getAddress();
    }

    @CStruct(value = "drasyl_peer")
    public interface DrasylPeerInfo extends PointerBase {
        @CFieldAddress("address")
        CCharPointer getAddress();
    }

    @CStruct(value = "drasyl_node_event")
    public interface DrasylNodeEvent extends PointerBase {
        @CField("event_code")
        int getEventCode();

        @CField("event_code")
        void setEventCode(int value);

        @CField("node")
        PointerBase getNode();

        @CField("node")
        void setNode(PointerBase value);

        @CField("peer")
        PointerBase getPeer();

        @CField("peer")
        void setPeer(PointerBase value);

        @CFieldAddress("message_sender")
        CCharPointer getMessageSender();

        @CField("message_payload")
        CCharPointer getMessagePayload();

        @CField("message_payload")
        void setMessagePayload(CCharPointer value);

        @CField("message_payload_len")
        int getMessagePayloadLength();

        @CField("message_payload_len")
        void setMessagePayloadLength(int value);
    }
}
