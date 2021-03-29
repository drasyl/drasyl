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

import io.reactivex.rxjava3.disposables.Disposable;
import org.drasyl.DrasylConfig;
import org.drasyl.DrasylException;
import org.drasyl.DrasylNode;
import org.drasyl.behaviour.Behavior;
import org.drasyl.behaviour.BehavioralDrasylNode;
import org.drasyl.behaviour.Behaviors;
import org.drasyl.event.Event;
import org.drasyl.event.NodeDownEvent;
import org.drasyl.event.NodeNormalTerminationEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.event.NodeUnrecoverableErrorEvent;
import org.drasyl.event.NodeUpEvent;
import org.drasyl.event.PeerDirectEvent;
import org.drasyl.event.PeerEvent;
import org.drasyl.event.PeerRelayEvent;
import org.drasyl.identity.CompressedPublicKey;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Duration;

import static java.time.Duration.ofSeconds;
import static javax.swing.JOptionPane.ERROR_MESSAGE;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

/**
 * This is an Example of a Chat Application running on the drasyl Overlay Network. It allows you to
 * send Text Messages to other drasyl Nodes running this Chat Application.
 */
@SuppressWarnings({ "java:S126", "java:S138", "java:S1188", "java:S1192", "java:S2096" })
public class ChatGui {
    private static final String IDENTITY = System.getProperty("identity", "chat-gui.identity.json");
    public static final Duration ONLINE_TIMEOUT = ofSeconds(10);
    private final JButton startShutdownButton = new JButton("Start");
    private final JFrame frame = new JFrame();
    private final JTextFieldWithPlaceholder recipientField = new JTextFieldWithPlaceholder(10, "Enter Recipient");
    private final JTextField messageField = new JTextFieldWithPlaceholder("Enter Message");
    private final JTextArea messagesArea = new JTextArea(30, 70);
    private final DrasylConfig config;
    private DrasylNode node;
    private Disposable onlineTimeoutDisposable;

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
                    messageField.setEditable(false);
                    node.send(recipient, messageField.getText()).whenComplete((result, e) -> {
                        if (e != null) {
                            JOptionPane.showMessageDialog(frame, e, "Error", ERROR_MESSAGE);
                        }
                        appendTextToMessageArea(" To " + recipient + ": " + messageField.getText() + "\n");
                        messageField.setText("");
                        messageField.setEditable(true);
                    });
                }
            }
        });

        // shutdown node on window close
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                shutdownNode();
            }
        });

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(EXIT_ON_CLOSE);
        frame.setVisible(true);

        recipientField.requestFocus();
    }

    private void run() throws DrasylException {
        node = new BehavioralDrasylNode(config) {
            @Override
            protected Behavior created() {
                return offline();
            }

            /**
             * Node is not connected to super peer.
             */
            private Behavior offline() {
                messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
                return Behaviors.receive()
                        .onEvent(NodeUpEvent.class, event -> {
                            appendTextToMessageArea("drasyl Node started. Connecting to super peer...\n");
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
                            appendTextToMessageArea("drasyl Node is connected to super peer. Relayed communication and discovery available.\n");
                            return online();
                        })
                        .onEvent(OnlineTimeout.class, event -> {
                            appendTextToMessageArea("No response from the Super Peer within " + ONLINE_TIMEOUT.toSeconds() + "s. Probably the Super Peer is unavailable or your configuration is faulty.\n");
                            return Behaviors.same();
                        })
                        .onEvent(PeerEvent.class, this::peerEvent)
                        .onMessage(String.class, this::messageEvent)
                        .onAnyEvent(event -> Behaviors.same())
                        .build();
            }

            /**
             * Node is connected to super peer.
             */
            private Behavior online() {
                messagesArea.setCaretPosition(messagesArea.getDocument().getLength());
                return Behaviors.receive()
                        .onEvent(NodeDownEvent.class, this::downEvent)
                        .onEvent(NodeOfflineEvent.class, event -> {
                            appendTextToMessageArea("drasyl Node lost connection to super peer. Relayed communication and discovery not available.\n");
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
                    onlineTimeoutDisposable.dispose();
                    onlineTimeoutDisposable = null;
                }
                recipientField.setEditable(false);
                messageField.setEditable(false);
                return offline();
            }

            /**
             * Reaction to a {@link org.drasyl.event.MessageEvent}.
             */
            private Behavior messageEvent(final CompressedPublicKey sender, final String payload) {
                appendTextToMessageArea(" From " + sender + ": " + payload + "\n");
                Toolkit.getDefaultToolkit().beep();
                return Behaviors.same();
            }

            /**
             * Reaction to a {@link PeerEvent}.
             */
            private Behavior peerEvent(final PeerEvent event) {
                if (event instanceof PeerDirectEvent) {
                    appendTextToMessageArea("Direct connection to " + event.getPeer().getPublicKey() + " available.\n");
                }
                else if (event instanceof PeerRelayEvent) {
                    appendTextToMessageArea("Relayed connection to " + event.getPeer().getPublicKey() + " available.\n");
                }
                return Behaviors.same();
            }

            /**
             * Signals that the node could not go online.
             */
            class OnlineTimeout implements Event {
            }
        };
        frame.setTitle("Chat: " + node.identity().getPublicKey().toString());
        appendTextToMessageArea("*******************************************************************************************************\n");
        appendTextToMessageArea("This is an Example of a Chat Application running on the drasyl Overlay Network.\n");
        appendTextToMessageArea("It allows you to send Text Messages to other drasyl Nodes running this Chat Application.\n");
        appendTextToMessageArea("Your address is " + node.identity().getPublicKey() + "\n");
        appendTextToMessageArea("*******************************************************************************************************\n");

        startNode();
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
}
