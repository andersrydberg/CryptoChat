import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;


/**
 * Handles input events from the user. Background tasks that need to be done in response to these events
 * are passed to the Model. Upon finishing these tasks the Model calls back to notify if any updates
 * need to be done to the user interface. Event handlers run in the JavaFx Application Thread.
 */
public class Controller {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private final Model model = new Model(this);
    // we will add a listener to this property to listen for changes
    private final SimpleObjectProperty<ConnectionState> connectionState = new SimpleObjectProperty<>();


    @FXML
    private TextField ipTextField;
    @FXML
    private Button mainButton;
    @FXML
    private Text displayText;
    @FXML
    private VBox publicKeyBox;
    @FXML
    private TextField ownKeyField;
    @FXML
    private TextField othersKeyField;
    @FXML
    private TextArea chatArea;
    @FXML
    private TextField chatTextField;



    /**
     * Runs after instantiation. Initializes some state and starts the server.
     */
    @FXML
    private void initialize() {

        // add a listener so that whenever the value of connectionState changes the lambda expression will fire
        connectionState.addListener((observableValue, oldValue, newValue) -> {
            switch (newValue) {
                case INACTIVE -> {
                    ipTextField.setDisable(false);
                    mainButton.setDisable(false);
                    mainButton.setText("Start session");
                    chatTextField.setDisable(true);
                    publicKeyBox.setDisable(true);
                    displayText.setText(NO_SESSION_MSG);
                }
                case CONNECTING -> {
                    ipTextField.setDisable(true);
                    mainButton.setDisable(false);
                    mainButton.setText("Cancel");
                    chatTextField.setDisable(true);
                    publicKeyBox.setDisable(true);
                }
                case CANCELLING_OUTGOING_CONNECTION, ENDING_SESSION -> {
                    ipTextField.setDisable(true);
                    mainButton.setDisable(true);
                    chatTextField.setDisable(true);
                }
                case ACTIVE_SESSION -> {
                    ipTextField.setDisable(true);
                    mainButton.setDisable(false);
                    mainButton.setText("Stop session");
                    chatTextField.setDisable(false);
                    publicKeyBox.setDisable(false);
                }
            }
        });

        // set to INACTIVE upon start
        connectionState.set(ConnectionState.INACTIVE);

        // start the server that will listen to incoming connections
        model.start();
    }



    /*
    // event handlers
     */

    /**
     * Called when user presses the Start/Cancel/Stop button.
     */
    @FXML
    private void buttonHandler(ActionEvent event) {
        switch (connectionState.get()) {
            case INACTIVE -> {
                // initiate an outgoing connection
                connectionState.set(ConnectionState.CONNECTING);
                model.connectTo(ipTextField.getText().trim());
            }
            case CONNECTING -> {
                // cancel the outgoing connection (e.g. before the connection has been rejected or accepted by the remote host)
                connectionState.set(ConnectionState.CANCELLING_OUTGOING_CONNECTION);
                model.cancelOutgoingConnection();
                connectionState.set(ConnectionState.INACTIVE);
            }
            case ACTIVE_SESSION -> {
                // end the active session
                connectionState.set(ConnectionState.ENDING_SESSION);
                model.stopActiveSession();
            }
        }
    }

    /**
     * Called when user presses the 'enter' key inside the chat text field
     */
    @FXML
    private void sendMessageHandler(ActionEvent event) {
        String message = chatTextField.getText().trim();

        if (message.isBlank()) {
            return;
        }

        chatTextField.clear();
        model.sendMessage(message);
        appendToChatArea("You: " + message);
    }



    /*
    // methods called by the model to update the view
    // these are called on other threads, so we wrap them with Platform.runLater
     */

    /**
     * Called when a session with a remote host has been established. Passes the public keys and remote host
     * address to the controller.
     * @param ownPublicKey the user's (digested) public key
     * @param othersPublicKey remote host's (digested) public key
     * @param address remote host's address
     */
    public void sessionStarted(String ownPublicKey, String othersPublicKey, String address) {
        Platform.runLater(() -> {
            ownKeyField.setText(ownPublicKey);
            othersKeyField.setText(othersPublicKey);

            connectionState.set(ConnectionState.ACTIVE_SESSION);
            displayText.setText("You have an ongoing session with " + address + " and can now chat securely. " +
                    "You may wish to confirm that the public keys displayed to you and to your chat partner are identical.");
        });
    }

    /**
     * Called when an active session has ended for any reason
     * (either remote host has quit or some irreparable error has occurred)
     */
    public void sessionEnded() {
        Platform.runLater(() -> {
            ownKeyField.clear();
            othersKeyField.clear();

            connectionState.set(ConnectionState.INACTIVE);
        });
    }

    /**
     * Called if remote host refuses a connection, or if there is an error connecting to the remote host.
     */
    public void outgoingConnectionFailed() {
        Platform.runLater(() -> connectionState.set(ConnectionState.INACTIVE));
    }

    /**
     * A public version of appendToChatArea
     */
    public void displayMessage(String message) {
        Platform.runLater(() -> appendToChatArea(message));
    }



    /*
    // Other methods
     */


    /**
     * Appends a time stamp and the passed message to the chat area
     * @param message the message to be displayed
     */
    private void appendToChatArea(String message) {
        chatArea.appendText(String.format(
                "%s %s\n",
                getTimeStamp(),
                message
        ));
    }

    /**
     * Convenience method for creating timestamps.
     * @return a timestamp in HH:mm:ss format
     */
    private String getTimeStamp() {
        return sdf.format(new Timestamp(System.currentTimeMillis()));
    }

    /**
     * Closes the server and any active session.
     */
    public void shutdown() {
        model.shutdown();
    }

}
