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
import org.drasyl.identity.Identity;
import org.drasyl.identity.IdentityPublicKey;
import org.drasyl.identity.IdentitySecretKey;
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
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

@SuppressWarnings("unused")
@CContext(LibDrasyl.Directives.class)
public class LibDrasyl {
    private static final UnsignedWord IDENTITY_PUBLIC_KEY_LENGTH = WordFactory.unsigned(IdentityPublicKey.KEY_LENGTH_AS_STRING);
    private static final UnsignedWord IDENTITY_SECRET_KEY_LENGTH = WordFactory.unsigned(IdentitySecretKey.KEY_LENGTH_AS_STRING);

    static {
        // disable all logging
        final Level level = Level.OFF;
        final Logger logger = Logger.getLogger("");
        logger.setLevel(level);
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static final short DRASYL_EVENT_NODE_UP = 10;
    private static final short DRASYL_EVENT_NODE_DOWN = 11;
    private static final short DRASYL_EVENT_NODE_ONLINE = 12;
    private static final short DRASYL_EVENT_NODE_OFFLINE = 13;
    private static final short DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR = 14;
    private static final short DRASYL_EVENT_NODE_NORMAL_TERMINATION = 15;
    private static final short DRASYL_EVENT_PEER_DIRECT = 20;
    private static final short DRASYL_EVENT_PEER_RELAY = 21;
    private static final short DRASYL_EVENT_LONG_TIME_ENCRYPTION = 22;
    private static final short DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION = 23;
    private static final short DRASYL_EVENT_MESSAGE = 30;
    private static final short DRASYL_EVENT_INBOUND_EXCEPTION = 40;
    private static DrasylNode node;
    private static boolean online;

    static final class Directives implements CContext.Directives {
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

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_init")
    private static int nodeInit(final IsolateThread thread,
                                final EventListener pointer) {
        if (node != null) {
            return -1;
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

                    final NodeEventType nodeEventType = StackValue.get(NodeEventType.class);

                    if (event instanceof NodeEvent) {
                        // node events
                        final NodeEvent nodeEvent = (NodeEvent) event;

                        // build identity struct
                        final IdentityType identityType = StackValue.get(IdentityType.class);
                        final Identity identity = nodeEvent.getNode().getIdentity();
                        identityType.setProofOfWork(identity.getProofOfWork().intValue());
                        CTypeConversion.toCString(identity.getIdentityPublicKey().toString(), UTF_8, identityType.getIdentityPublicKey(), IDENTITY_PUBLIC_KEY_LENGTH);
                        CTypeConversion.toCString(identity.getIdentitySecretKey().toUnmaskedString(), UTF_8, identityType.getIdentitySecretKey(), IDENTITY_SECRET_KEY_LENGTH);

                        // build node struct
                        final DrasylNodeInfo drasylNodeInfo = StackValue.get(DrasylNodeInfo.class);
                        drasylNodeInfo.setIdentity(identityType);

                        // update event struct
                        nodeEventType.setNode(drasylNodeInfo);

                        if (nodeEvent instanceof NodeUpEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_UP);
                        }
                        else if (nodeEvent instanceof NodeDownEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_DOWN);
                        }
                        else if (nodeEvent instanceof NodeOnlineEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_ONLINE);
                        }
                        else if (nodeEvent instanceof NodeOfflineEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_OFFLINE);
                        }
                        else if (nodeEvent instanceof NodeUnrecoverableErrorEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_UNRECOVERABLE_ERROR);
                        }
                        else if (nodeEvent instanceof NodeNormalTerminationEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_NODE_NORMAL_TERMINATION);
                        }
                    }
                    else if (event instanceof PeerEvent) {
                        // peer events
                        final PeerEvent peerEvent = (PeerEvent) event;

                        // build peer struct
                        final PeerType peerType = StackValue.get(PeerType.class);
                        nodeEventType.setPeer(peerType);
                        CTypeConversion.toCString(peerEvent.getPeer().getAddress().toString(), UTF_8, peerType.getAddress(), IDENTITY_PUBLIC_KEY_LENGTH);

                        if (peerEvent instanceof PeerDirectEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_PEER_DIRECT);
                        }
                        else if (peerEvent instanceof PeerRelayEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_PEER_RELAY);
                        }
                        else if (peerEvent instanceof LongTimeEncryptionEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_LONG_TIME_ENCRYPTION);
                        }
                        else if (peerEvent instanceof PerfectForwardSecrecyEncryptionEvent) {
                            nodeEventType.setEventCode(DRASYL_EVENT_PERFECT_FORWARD_SECRECY_ENCRYPTION);
                        }
                    }
                    // message events
                    else if (event instanceof MessageEvent) {
                        CTypeConversion.toCString(((MessageEvent) event).getSender().toString(), UTF_8, nodeEventType.getMessageSender(), IDENTITY_PUBLIC_KEY_LENGTH);
                        final String payload = ((MessageEvent) event).getPayload().toString();
                        nodeEventType.setMessagePayloadLength(payload.length());
                        final CTypeConversion.CCharPointerHolder cCharPointerHolder = CTypeConversion.toCString(payload);
                        nodeEventType.setMessagePayload(cCharPointerHolder.get());
                        nodeEventType.setEventCode(DRASYL_EVENT_MESSAGE);
                    }
                    else if (event instanceof InboundExceptionEvent) {
                        nodeEventType.setEventCode(DRASYL_EVENT_INBOUND_EXCEPTION);
                    }
                    else {
                        nodeEventType.setEventCode((short) 0);
                    }

                    pointer.invoke(thread, nodeEventType);
                }
            };
            return 0;
        }
        catch (final Exception e) {
            return -1;
        }
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_identity")
    private static int nodeIdentity(final IsolateThread thread, final IdentityTypePointer identityTypePointer) {
        if (node == null) {
            return -1;
        }

        final Identity identity = node.identity();
        final IdentityType identityType = StackValue.get(IdentityType.class);
        identityType.setProofOfWork(identity.getProofOfWork().intValue());
        final String pk = identity.getIdentityPublicKey().toString();
        System.out.println("pk = " + pk);
        System.out.println("pk.len = " + pk.length());
        CTypeConversion.toCString(pk, UTF_8, identityType.getIdentityPublicKey(), IDENTITY_PUBLIC_KEY_LENGTH);
        final String sk = identity.getIdentitySecretKey().toUnmaskedString();
        System.out.println("sk = " + sk);
        System.out.println("sk.len = " + sk.length());
        CTypeConversion.toCString(sk, UTF_8, identityType.getIdentitySecretKey(), IDENTITY_SECRET_KEY_LENGTH);
        System.out.println("pk read = '" +CTypeConversion.toJavaString(identityType.getIdentityPublicKey(), IDENTITY_PUBLIC_KEY_LENGTH, UTF_8) + "'");
        System.out.println("sk read = '" +CTypeConversion.toJavaString(identityType.getIdentitySecretKey(), IDENTITY_SECRET_KEY_LENGTH, UTF_8) + "'");
        identityTypePointer.write(identityType);

        return 0;
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_start")
    private static int nodeStart(final IsolateThread thread) {
        if (node == null) {
            return -1;
        }

        try {
            node.start().toCompletableFuture().join();
            return 0;
        }
        catch (final Exception e) {
            return -1;
        }
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_stop")
    private static int nodeStop(final IsolateThread thread) {
        if (node == null) {
            return -1;
        }

        try {
            node.shutdown().toCompletableFuture().join();
            node = null;
            return 0;
        }
        catch (final Exception e) {
            return -1;
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
                return -1;
            }
            return 0;
        }
        catch (final Exception e) {
            return -1;
        }
    }

    @SuppressWarnings({ "java:S1166", "java:S2221" })
    @CEntryPoint(name = "drasyl_node_send")
    private static int nodeSend(final IsolateThread thread,
                                final CCharPointer recipientPointer,
                                final CCharPointer payloadPointer,
                                final UnsignedWord payloadLength) {
        if (node == null) {
            return -1;
        }

        try {
            final String recipient = CTypeConversion.toJavaString(recipientPointer, IDENTITY_PUBLIC_KEY_LENGTH, UTF_8);
            final String payload = CTypeConversion.toJavaString(payloadPointer, payloadLength, UTF_8);
            node.send(recipient, payload).toCompletableFuture().join();
            return 0;
        }
        catch (final Exception e) {
            return -1;
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

    /**
     * C function with parameters {@code (graal_isolatethread_t*, drasyl_event_t*)} that consumes
     * {@link Event}s.
     */
    private interface EventListener extends CFunctionPointer {
        @SuppressWarnings("UnusedReturnValue")
        @InvokeCFunctionPointer
        int invoke(IsolateThread thread, NodeEventType event);
    }

    /**
     * C struct representing {@link org.drasyl.identity.Identity}.
     */
    @CStruct(value = "drasyl_identity_t")
    private interface IdentityType extends PointerBase {
        @CField("proof_of_work")
        int getProofOfWork();

        @CField("proof_of_work")
        void setProofOfWork(int value);

        @CFieldAddress("identity_public_key")
        CCharPointer getIdentityPublicKey();

        @CFieldAddress("identity_secret_key")
        CCharPointer getIdentitySecretKey();
    }

    @CPointerTo(IdentityType.class)
    public interface IdentityTypePointer extends PointerBase {
        IdentityType read();

        void write(IdentityType identityType);
    }

    /**
     * C struct representing {@link org.drasyl.node.event.Node}.
     */
    @CStruct(value = "drasyl_node_t")
    private interface DrasylNodeInfo extends PointerBase {
        @CField("identity")
        PointerBase getIdentity();

        @CField("identity")
        void setIdentity(PointerBase value);
    }

    /**
     * C struct representing {@link org.drasyl.node.event.Peer}.
     */
    @CStruct(value = "drasyl_peer_t")
    private interface PeerType extends PointerBase {
        @CFieldAddress("address")
        CCharPointer getAddress();
    }

    /**
     * C struct representing {@link org.drasyl.node.event.NodeEvent}.
     */
    @CStruct(value = "drasyl_event_t")
    private interface NodeEventType extends PointerBase {
        @AllowNarrowingCast
        @CField("event_code")
        void setEventCode(short value);

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
        void setMessagePayload(CCharPointer value);

        @AllowNarrowingCast
        @CField("message_payload_len")
        void setMessagePayloadLength(int value);
    }
}
