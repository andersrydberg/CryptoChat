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


    private final Backend backend = new Backend(this);

    private final SimpleObjectProperty<ButtonState> buttonState = new SimpleObjectProperty<>(ButtonState.START);


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
        buttonState.addListener((observableValue, oldValue, newValue) -> {
            switch (newValue) {
                case START -> {
                    chatInputField.setDisable(true);
                    ipField.setEditable(true);
                    mainButton.setText("Start session");
                }
                case CANCEL -> {
                    ipField.setEditable(false);
                    mainButton.setText("Cancel");
                }
                case STOP -> {
                    ipField.setEditable(false);
                    mainButton.setText("Stop session");
                    chatInputField.setDisable(false);
                }
            }
        });
        backend.start();
    }



    /*
    // action handlers, etc.
     */

    /**
     * Called when user presses the Start/Cancel/Stop button.
     */
    public void buttonHandler(ActionEvent event) {
        switch (buttonState.get()) {
            case START -> startHandler();
            case CANCEL -> cancelHandler();
            case STOP -> stopHandler();
        }
    }

    private void startHandler() {
        buttonState.set(ButtonState.CANCEL);
        backend.initializeOutgoingConnection(ipField.getText().trim());
    }

    private void cancelHandler() {
        backend.cancelConnection();
        buttonState.set(ButtonState.START);
    }

    private void stopHandler() {
        backend.cancelConnection();
        buttonState.set(ButtonState.START);
    }

    // called with Platform.runLater
    public void outgoingConnectionEstablished() {
        buttonState.set(ButtonState.STOP);
    }

    // called with Platform.runLater
    public void outgoingConnectionFailed() {
        buttonState.set(ButtonState.START);
    }

    public void sendMessageHandler(ActionEvent event) {

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
