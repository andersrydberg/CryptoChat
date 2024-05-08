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


    private ButtonState buttonState = ButtonState.START;


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
        backend.start();
    }



    /*
    // action handlers, etc.
     */

    /**
     * Called when user presses the Start/Cancel/Stop button.
     */
    public void buttonHandler(ActionEvent event) {
        switch (buttonState) {
            case START -> startSession();
            case CANCEL -> cancelConnection();
            case STOP -> stopSession();
        }
    }

    private void startSession() {
        ipField.setEditable(false);
        mainButton.setText("Cancel");
        buttonState = ButtonState.CANCEL;

        backend.outgoingConnection(ipField.getText().trim());
    }

    private void cancelConnection() {
        backend.closeSession();

        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
    }

    private void stopSession() {
        backend.closeSession();

        chatInputField.setEditable(false);
        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
        displayText.setText(NO_SESSION_MSG);
    }

    public void outgoingConnectionEstablished() {
        ipField.setEditable(false);
        mainButton.setText("Stop session");
        chatInputField.setEditable(true);
        buttonState = ButtonState.STOP;
    }

    public void outgoingConnectionFailed() {
        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
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
