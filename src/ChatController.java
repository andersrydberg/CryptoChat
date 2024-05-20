import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;


public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");


    private final ChatBackend chatBackend = new ChatBackend(this);

    private final SimpleObjectProperty<ConnectionState> connectionState = new SimpleObjectProperty<>(ConnectionState.INACTIVE);


    @FXML
    private TextField ipField;
    @FXML
    private Button mainButton;
    @FXML
    private Text displayText;
    @FXML
    private VBox publicKeyBox;
    @FXML
    private TextField ownKeyField;
    @FXML
    private TextField contactPublicKey;
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatInputField;



    /**
     * Runs after the controller is constructed.
     */
    @FXML
    private void initialize() {
        connectionState.addListener((observableValue, oldValue, newValue) -> {
            switch (newValue) {
                case INACTIVE -> {
                    ipField.setDisable(false);
                    mainButton.setDisable(false);
                    mainButton.setText("Start session");
                    chatInputField.setDisable(true);
                }
                case CONNECTING -> {
                    ipField.setDisable(true);
                    mainButton.setDisable(false);
                    mainButton.setText("Cancel");
                    chatInputField.setDisable(true);
                }
                case CANCELLING_OUTGOING_CONNECTION, ENDING_SESSION -> {
                    ipField.setDisable(true);
                    mainButton.setDisable(true);
                    chatInputField.setDisable(true);
                }
                case ACTIVE_SESSION -> {
                    ipField.setDisable(true);
                    mainButton.setDisable(false);
                    mainButton.setText("Stop session");
                    chatInputField.setDisable(false);
                }
            }
        });
        chatBackend.start();
    }



    /*
    // action handlers, etc.
     */

    /**
     * Called when user presses the Start/Cancel/Stop button.
     */
    public void buttonHandler(ActionEvent event) {
        switch (connectionState.get()) {
            case INACTIVE -> startHandler();
            case CONNECTING -> cancelHandler();
            case ACTIVE_SESSION -> stopHandler();
        }
    }

    private void startHandler() {
        connectionState.set(ConnectionState.CONNECTING);
        chatBackend.connectTo(ipField.getText().trim());
    }

    private void cancelHandler() {
        connectionState.set(ConnectionState.CANCELLING_OUTGOING_CONNECTION);
        chatBackend.cancelOutgoingConnection();
        connectionState.set(ConnectionState.INACTIVE);
    }

    private void stopHandler() {
        connectionState.set(ConnectionState.ENDING_SESSION);
        chatBackend.stopActiveSession();

        ownKeyField.clear();
        ownKeyField.setDisable(true);
        contactPublicKey.clear();
        contactPublicKey.setDisable(true);

        connectionState.set(ConnectionState.INACTIVE);
    }

    // callback methods (from chatBackend)


    // called with Platform.runLater
    public void sessionStarted(String ownPublicKey, String othersPublicKey) {
        ownKeyField.setText(ownPublicKey);
        ownKeyField.setDisable(false);
        contactPublicKey.setText(othersPublicKey);
        contactPublicKey.setDisable(false);

        connectionState.set(ConnectionState.ACTIVE_SESSION);
    }

    // called with Platform.runLater
    public void outgoingConnectionFailed() {
        connectionState.set(ConnectionState.INACTIVE);
    }



    public void sendMessageHandler(ActionEvent event) {
        String message = chatInputField.getText().trim();

        if (message.isBlank()) {
            return;
        }

        chatInputField.clear();
        chatBackend.sendMessage(message);
        appendToChat(message);
    }


    /**
     * Closes the server socket and current session socket, if they exist.
     */
    public void shutdown() {
//        if (serverSocket != null) {
//            try {
//                serverSocket.close();
//            } catch (IOException e) {
//                // ignore;
//            }
//        }
//
//        if (currentSessionSocket != null) {
//            try {
//                currentSessionSocket.close();
//            } catch (IOException e) {
//                // ignore
//            }
//        }
    }





    /*
    // Other methods
     */

    // called with Platform.runLater
    public void appendToChat(String message) {
        chatArea.appendText(String.format(
                "%s: %s\n",
                getTimeStamp(),
                message
        ));
    }

    /**
     * Convenience method for creating timestamps.
     */
    private String getTimeStamp() {
        return sdf.format(new Timestamp(System.currentTimeMillis()));
    }

}
