import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;


public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final int DEFAULT_PORT = 27119;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private Socket currentSessionSocket;
    private OutgoingConnectionTask outgoingConnectionTask;

    private ButtonState buttonState = ButtonState.START;

    // private int noOfRetries = 0;

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
        // threadPool = Executors.newFixedThreadPool(10);
        // startupServer();
        Backend backend = new Backend(this);
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

        outgoingConnectionTask = new OutgoingConnectionTask(ipField.getText().trim(), DEFAULT_PORT);
        outgoingConnectionTask.setOnSucceeded(this::outgoingConnectionSucceeded);
        outgoingConnectionTask.setOnFailed(this::outgoingConnectionFailed);

        buttonState = ButtonState.CANCEL;

        Thread thread = new Thread(outgoingConnectionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void cancelConnection() {
        outgoingConnectionTask.shutdown();

        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
    }

    private void stopSession() {
        try {
            currentSessionSocket.close();
        } catch (IOException e) {
            // ignore;
        } finally {
            chatInputField.setEditable(false);
            currentSessionSocket = null;
            ipField.setEditable(true);
            mainButton.setText("Start session");
            buttonState = ButtonState.START;
            displayText.setText(NO_SESSION_MSG);
        }
    }

    private void outgoingConnectionSucceeded(WorkerStateEvent workerStateEvent) {
        try {
            currentSessionSocket = outgoingConnectionTask.get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        ipField.setEditable(false);
        mainButton.setText("Stop session");
        chatInputField.setEditable(true);
        buttonState = ButtonState.STOP;
    }

    private void outgoingConnectionFailed(WorkerStateEvent workerStateEvent) {
        String host = ((OutgoingConnectionTask)workerStateEvent.getSource()).getHost();
        chatArea.appendText(String.format("%s: Could not connect to %s.\n", getTimeStamp(), host));

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

    public boolean hasOngoingSession() {
        return currentSessionSocket != null;
    }

    public void receiveSocket(Socket socket) {
        currentSessionSocket = socket;
    }
}
