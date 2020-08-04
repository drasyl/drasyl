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
package org.drasyl.example.chat.gui;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.drasyl.DrasylNode;
import org.drasyl.crypto.CryptoException;
import org.drasyl.event.Event;
import org.drasyl.event.MessageEvent;
import org.drasyl.event.NodeEvent;
import org.drasyl.event.NodeOfflineEvent;
import org.drasyl.event.NodeOnlineEvent;
import org.drasyl.identity.CompressedPublicKey;
import org.drasyl.util.Pair;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * A simple JavaFX Chat that uses drasyl as backend overlay network.
 */
public class ChatGUI extends Application {
    private static final String ERROR_CSS = "error";
    private Scene chatScene;
    private TextField usernameInput;
    private TextField recipientIDInput;
    private TextField chatField;
    private TextArea txtArea;
    private CompressedPublicKey recipient;
    private CompressedPublicKey myID;
    private String username;
    private Stage stage;
    private DrasylNode node;

    @SuppressWarnings({ "java:S112" })
    @Override
    public void start(Stage stage) {
        try {
            this.stage = stage;
            stage.setTitle("Drasyl Chat");
            stage.getIcons().add(new Image("icon.png"));

            chatScene = buildChatScene();
            chatScene.getStylesheets().add("drasyl.css");

            CompletableFuture<Void> online = new CompletableFuture<>();
            node = new DrasylNode() {
                @Override
                public void onEvent(Event event) {
                    if (event instanceof MessageEvent) {
                        parseMessage(((MessageEvent) event).getMessage());
                    }
                    else if (event instanceof NodeOnlineEvent) {
                        if (!online.isDone()) {
                            online.complete(null);
                        }
                        myID = ((NodeEvent) event).getNode().getIdentity().getPublicKey();
                        txtArea.appendText("[~System~]: The node is online. Your address is: " + myID + "\n");
                    }
                    else if (event instanceof NodeOfflineEvent) {
                        txtArea.appendText("[~System~]: The node is offline. No messages can be sent at the moment. Wait until node comes back online.\n");
                    }
                }
            };
            node.start().join();
            online.join();

            Scene initScene = buildInitScene();
            initScene.getStylesheets().add("drasyl.css");

            stage.setScene(initScene);
            stage.show();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (node != null) {
            node.shutdown().join();
            System.exit(0);
        }
    }

    private Scene buildChatScene() {
        VBox vBox = new VBox();
        HBox hBox = new HBox();

        ScrollPane scrollPane = new ScrollPane();
        txtArea = new TextArea();
        txtArea.setEditable(false);
        scrollPane.setContent(txtArea);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);

        chatField = new TextField();
        chatField.setOnAction(this::newInputAction);
        chatField.requestFocus();

        hBox.getChildren().addAll(chatField);
        HBox.setHgrow(chatField, Priority.ALWAYS);

        vBox.getChildren().addAll(scrollPane, hBox);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        return new Scene(vBox, 800, 600);
    }

    private void parseMessage(Pair<CompressedPublicKey, Object> payload) {
        if (payload.second() instanceof Message) {
            Message msg = (Message) payload.second();

            txtArea.appendText("[" + msg.getUsername() + "]: " + msg.getMsg() + "\n");
        }
    }

    private Scene buildInitScene() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setVgap(8);
        grid.setHgap(10);
        grid.setAlignment(Pos.CENTER);

        Label myIDLabel = new Label("Your ID is: ");
        GridPane.setConstraints(myIDLabel, 0, 0);
        TextField myIDTextField = new TextField(myID.toString());
        myIDTextField.setEditable(false);
        GridPane.setConstraints(myIDTextField, 1, 0);

        Label usernameLabel = new Label("Your username: ");
        GridPane.setConstraints(usernameLabel, 0, 1);
        usernameInput = new TextField("");
        GridPane.setConstraints(usernameInput, 1, 1);

        Label recipientIDLabel = new Label("Recipient ID: ");
        GridPane.setConstraints(recipientIDLabel, 0, 2);
        recipientIDInput = new TextField("");
        GridPane.setConstraints(recipientIDInput, 1, 2);

        Button startButton = new Button("Start chatting");
        startButton.setOnAction(this::initAction);
        GridPane.setConstraints(startButton, 1, 3);
        grid.getChildren().addAll(myIDLabel, myIDTextField, usernameLabel, usernameInput, recipientIDLabel, recipientIDInput, startButton);

        return new Scene(grid, 325, 225);
    }

    private void newInputAction(ActionEvent actionEvent) {
        if (validateInput(chatField, s -> !s.isEmpty())) {
            try {
                final String msg = chatField.getText();

                node.send(recipient, new Message(msg, username))
                        .exceptionally(e -> {
                            txtArea.appendText("Message `" + msg + "` cloud not be sent.");
                            return null;
                        });
                txtArea.appendText("[" + username + "]: " + chatField.getText() + "\n");
            }
            finally {
                chatField.clear();
            }
        }
    }

    @SuppressWarnings({ "java:S112" })
    private void initAction(ActionEvent actionEvent) {
        if (validateInput(usernameInput, s -> !s.isEmpty())
//                && validateInput(recipientIDInput, Address::isValid)
        ) {
            username = usernameInput.getText();
            stage.setTitle("[" + username + "] Drasyl Chat");
            try {
                recipient = CompressedPublicKey.of(recipientIDInput.getText());
            }
            catch (CryptoException e) {
                throw new RuntimeException(e);
            }

            stage.setScene(chatScene);
        }
    }

    private boolean validateInput(TextField textField,
                                  Function<String, Boolean> validation) { //NOSONAR
        if (validation.apply(textField.getText())) { //NOSONAR
            textField.getStyleClass().remove(ERROR_CSS);

            return true;
        }
        else {
            textField.getStyleClass().add(ERROR_CSS);

            return false;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}