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
package org.drasyl.example.chat;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.WriteTimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.internal.ThrowableUtil;
import org.drasyl.channel.DrasylChannel;
import org.drasyl.handler.arq.stopandwait.ByteToStopAndWaitArqDataCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqCodec;
import org.drasyl.handler.arq.stopandwait.StopAndWaitArqHandler;
import org.drasyl.handler.connection.ConnectionHandshakeCodec;
import org.drasyl.handler.connection.ConnectionHandshakeGuard;
import org.drasyl.handler.connection.ConnectionHandshakeHandler;
import org.drasyl.handler.connection.ConnectionHandshakeWriteEnqueuer;
import org.drasyl.identity.DrasylAddress;
import org.drasyl.node.DrasylConfig;
import org.drasyl.node.DrasylException;
import org.drasyl.node.DrasylNode;
import org.drasyl.node.behaviour.Behavior;
import org.drasyl.node.behaviour.BehavioralDrasylNode;
import org.drasyl.node.behaviour.Behaviors;
import org.drasyl.node.channel.DrasylNodeChannelInitializer;
import org.drasyl.node.event.Event;
import org.drasyl.node.event.LongTimeEncryptionEvent;
import org.drasyl.node.event.NodeDownEvent;
import org.drasyl.node.event.NodeNormalTerminationEvent;
import org.drasyl.node.event.NodeOfflineEvent;
import org.drasyl.node.event.NodeOnlineEvent;
import org.drasyl.node.event.NodeUnrecoverableErrorEvent;
import org.drasyl.node.event.NodeUpEvent;
import org.drasyl.node.event.PeerDirectEvent;
import org.drasyl.node.event.PeerEvent;
import org.drasyl.node.event.PeerRelayEvent;
import org.drasyl.node.event.PerfectForwardSecrecyEncryptionEvent;
import org.drasyl.node.handler.serialization.Serialization;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;
import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings({ "java:S126", "java:S138", "java:S1188", "java:S1192", "java:S2096" })
public class ChatGui {
    private static final String IDENTITY = System.getProperty("identity", "chat-gui.identity");
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    public static final int RETRY_MILLIS = 500;
    public static final int TIMEOUT_SECONDS = 5;
    private final JButton startShutdownButton = new JButton("Start");
    private final JFrame frame = new JFrame();
    private final JTextFieldWithPlaceholder recipientField = new JTextFieldWithPlaceholder(10, "Enter Recipient");
    private final JTextField messageField = new JTextFieldWithPlaceholder("Enter Message");
    private final JTextArea messagesArea = new JTextArea(30, 70);
    private final DrasylConfig config;
    private DrasylNode node;
    private Future<?> onlineTimeoutDisposable;

    @SuppressWarnings("java:S3776")
    public ChatGui(final DrasylConfig config) {
        this.config = config;

        // initial fields state
        startShutdownButton.setEnabled(false);
        recipientField.setEditable(false);
        messageField.setEditable(false);
        messagesArea.setEditable(false);

        // control
        frame.getContentPane().add(startShutdownButton, BorderLayout.NORTH);

        // output
        frame.getContentPane().add(new JScrollPane(messagesArea), BorderLayout.CENTER);

        // input
        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(recipientField, BorderLayout.WEST);
        panel.add(messageField, BorderLayout.CENTER);
        frame.getContentPane().add(panel, BorderLayout.SOUTH);

        startShutdownButton.addActionListener(e -> {
            if ("Start".equals(startShutdownButton.getText())) {
                startNode();
            }
            else {
                shutdownNode();
            }
        });

        // send message on enter
        messageField.addActionListener(event -> {
            if (node != null) {
                final String recipient = recipientField.getText().trim();
                if (!recipient.isBlank()) {
                    final String text = messageField.getText();
                    messageField.setText("");
                    appendTextToMessageArea(" To " + recipient + ": " + text + "\n");
                    node.send(recipient, text).whenComplete((result, e) -> {
                        if (e != null && !(e instanceof ClosedChannelException)) {
                            appendTextToMessageArea("Unable to send message: " + ThrowableUtil.stackTraceToString(e) + "\n");
                        }
                    });
                }
            }
        });

        // shutdown node on window close
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                if (node != null) {
                    shutdownNode();
                }
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        frame.setVisible(true);

        recipientField.requestFocus();
    }

    private void run() throws DrasylException {
        frame.setTitle("Create Node...");
        node = new ChatGuiNode();
        frame.setTitle("Chat: " + node.identity().getIdentityPublicKey().toString());
        appendTextToMessageArea("*******************************************************************************************************\n");
        appendTextToMessageArea("This is an Example of a Chat Application running on the drasyl Overlay Network.\n");
        appendTextToMessageArea("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.\n");
        appendTextToMessageArea("Your address is " + node.identity().getIdentityPublicKey() + "\n");
        appendTextToMessageArea("*******************************************************************************************************\n");

        startNode();

        // trigger loading of the inheritance graph (otherwise it will be loaded lazilly when user
        // is sending the first message causing the UI to lag)
        Serialization.noop();
    }

    private void startNode() {
        startShutdownButton.setEnabled(false);
        node.start().whenComplete((result, e) -> {
            startShutdownButton.setText("Shutdown");
            startShutdownButton.setEnabled(true);
        });
    }

    private void shutdownNode() {
        startShutdownButton.setEnabled(false);
        node.shutdown().whenComplete((result, e) -> {
            startShutdownButton.setText("Start");
            startShutdownButton.setEnabled(true);
        });
    }

    private void appendTextToMessageArea(final String text) {
        messagesArea.append(text);
        messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
    }

    public static void main(final String[] args) throws DrasylException {
        final DrasylConfig config = DrasylConfig.newBuilder()
                .identityPath(Path.of(IDENTITY))
                .build();

        final ChatGui gui = new ChatGui(config);
        gui.run();
    }

    private class ChatGuiNode extends BehavioralDrasylNode {
        public ChatGuiNode() throws DrasylException {
            super(ChatGui.this.config);

            bootstrap.childHandler(new DrasylNodeChannelInitializer(ChatGui.this.config, this) {
                @Override
                protected void firstStage(final DrasylChannel ch) {
                    super.firstStage(ch);

                    final ChannelPipeline p = ch.pipeline();

                    // perform handshake to ensure both connections are synchronized
                    p.addLast(new ConnectionHandshakeCodec());
                    p.addLast(new ConnectionHandshakeHandler(10_000L, true));
                    p.addLast(new ConnectionHandshakeGuard());
                    p.addLast(new ConnectionHandshakeWriteEnqueuer());

                    // ensure that messages are delivered
                    p.addLast(new StopAndWaitArqCodec());
                    p.addLast(new StopAndWaitArqHandler(RETRY_MILLIS));
                    p.addLast(new ByteToStopAndWaitArqDataCodec());
                    p.addLast(new WriteTimeoutHandler(TIMEOUT_SECONDS));
                    p.addLast(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void exceptionCaught(final ChannelHandlerContext ctx,
                                                    final Throwable cause) {
                            if (cause instanceof WriteTimeoutException) {
                                appendTextToMessageArea("Message to " + ctx.channel().remoteAddress() + " was not acknowledged. Maybe recipient is offline/unreachable?\n");
                            }
                            else {
                                ctx.fireExceptionCaught(cause);
                            }
                        }
                    });
                }
            });
        }

        @Override
        protected Behavior created() {
            return offline();
        }

        /**
         * Node is not connected to any super peer.
         */
        private Behavior offline() {
            messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
            return newBehaviorBuilder()
                    .onEvent(NodeUpEvent.class, event -> {
                        appendTextToMessageArea("drasyl Node started. Connecting to super peer(s)...\n");
                        recipientField.setEditable(true);
                        messageField.setEditable(true);
                        return Behaviors.withScheduler(scheduler -> {
                            onlineTimeoutDisposable = scheduler.scheduleEvent(new OnlineTimeout(), ONLINE_TIMEOUT);
                            return offline();
                        });
                    })
                    .onEvent(NodeUnrecoverableErrorEvent.class, event -> {
                        appendTextToMessageArea("drasyl Node encountered an unrecoverable error: " + event.getError().getMessage() + " \n");
                        return Behaviors.shutdown();
                    })
                    .onEvent(NodeNormalTerminationEvent.class, event -> {
                        appendTextToMessageArea("drasyl Node has been shut down.\n");
                        return Behaviors.same();
                    })
                    .onEvent(NodeDownEvent.class, this::downEvent)
                    .onEvent(NodeOnlineEvent.class, event -> {
                        appendTextToMessageArea("drasyl Node is connected to at least one super peer. Relayed communication and Internet discovery available.\n");
                        return online();
                    })
                    .onEvent(OnlineTimeout.class, event -> {
                        appendTextToMessageArea("No response from any super peer within " + ONLINE_TIMEOUT.toSeconds() + "s. Probably all super peers are unavailable, your configuration is faulty, or system time is wrong.\n");
                        return Behaviors.same();
                    })
                    .onEvent(PeerEvent.class, this::peerEvent)
                    .onMessage(String.class, this::messageEvent)
                    .onAnyEvent(event -> Behaviors.same())
                    .build();
        }

        /**
         * Node is connected to at least one super peer.
         */
        private Behavior online() {
            messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
            return newBehaviorBuilder()
                    .onEvent(NodeDownEvent.class, this::downEvent)
                    .onEvent(NodeOfflineEvent.class, event -> {
                        appendTextToMessageArea("drasyl Node lost connection to all super peers. Relayed communication and Internet discovery not available.\n");
                        return Behaviors.same();
                    })
                    .onEvent(PeerEvent.class, this::peerEvent)
                    .onMessage(String.class, this::messageEvent)
                    .onAnyEvent(event -> Behaviors.same())
                    .build();
        }

        /**
         * Reaction to a {@link NodeDownEvent}.
         */
        private Behavior downEvent(final NodeDownEvent event) {
            appendTextToMessageArea("drasyl Node is shutting down. No more communication possible.\n");
            if (onlineTimeoutDisposable != null) {
                onlineTimeoutDisposable.cancel(false);
                onlineTimeoutDisposable = null;
            }
            recipientField.setEditable(false);
            messageField.setEditable(false);
            return offline();
        }

        /**
         * Reaction to a {@link org.drasyl.node.event.MessageEvent}.
         */
        private Behavior messageEvent(final DrasylAddress sender, final String payload) {
            appendTextToMessageArea(" From " + sender + ": " + payload + "\n");
            Toolkit.getDefaultToolkit().beep();
            return Behaviors.same();
        }

        /**
         * Reaction to a {@link PeerEvent}.
         */
        private Behavior peerEvent(final PeerEvent event) {
            if (event instanceof PeerDirectEvent) {
                appendTextToMessageArea("Direct connection to " + event.getPeer().getAddress() + " available.\n");
            }
            else if (event instanceof PeerRelayEvent) {
                appendTextToMessageArea("Relayed connection to " + event.getPeer().getAddress() + " available.\n");
            }
            else if (event instanceof PerfectForwardSecrecyEncryptionEvent) {
                appendTextToMessageArea("Using now perfect forwarded encryption with peer " + event.getPeer() + "\n");
            }
            else if (event instanceof LongTimeEncryptionEvent) {
                appendTextToMessageArea("Using now long time encryption with peer " + event.getPeer() + "\n");
            }
            return Behaviors.same();
        }

        /**
         * Signals that the node could not go online.
         */
        class OnlineTimeout implements Event {
        }
    }

    @SuppressWarnings({ "java:S110" })
    public static class JTextFieldWithPlaceholder extends JTextField {
        private final String placeholder;

        public JTextFieldWithPlaceholder(final int columns, final String placeholder) {
            super(columns);
            this.placeholder = placeholder;
        }

        public JTextFieldWithPlaceholder(final String placeholder) {
            this(0, placeholder);
        }

        @Override
        public void paintComponent(final Graphics g) {
            super.paintComponent(g);

            if (super.getText().length() > 0 || placeholder == null) {
                return;
            }

            final Graphics2D g2 = (Graphics2D) g;

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(super.getDisabledTextColor());
            g2.drawString(placeholder, getInsets().left, g.getFontMetrics().getMaxAscent() + getInsets().top);
        }
    }
}
