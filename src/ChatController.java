import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final int DEFAULT_PORT = 27119;

    private ServerSocket serverSocket;
    private Socket currentSessionSocket;

    public TextField ipField;
    public Button mainButton;
    public Text displayText;
    public VBox publicKeyBox;
    public TextField ownKeyField;
    public TextField contactPublicKey;
    public TextArea chatArea;
    public TextField chatInputField;

    /**
     * Runs after the controller is constructed. Starts a server socket on the default port.
     */
    @FXML
    public void initialize() {

    }

    public void buttonHandler(ActionEvent event) {
        ipField.setEditable(false);
        mainButton.setDisable(true);

        ConnectionTask task = new ConnectionTask(ipField.getText().trim(), DEFAULT_PORT);
//        task.messageProperty().
//                addListener((observableValue, oldValue, newValue)
//                        -> printlnToLog(newValue)
//                );
//        task.setOnFailed(this::sendFailedHandler);
//        task.setOnSucceeded(this::sendSucceededHandler);
        new Thread(task).start();

    }

    public void sendMessageHandler(ActionEvent event) {

    }

    /**
     * Sets the serverSocket field so that it can be closed if needed
     * (kills the thread even if it is in a blocking call)
     */
    public void receiveServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }



    /**
     * Closes the server socket so that any associated threads die.
     */
    public void shutdown() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

}
