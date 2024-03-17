import javafx.application.Platform;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;


public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final int DEFAULT_PORT = 27119;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    private ServerStartupTask serverStartupTask;
    private ServerSocket serverSocket;
    private Socket currentSessionSocket;
    private ButtonState buttonState = ButtonState.START;
    private OutgoingConnectionTask outgoingConnectionTask;

    private int noOfRetries = 0;

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
    public void initialize() {
        startupServer();
    }



    /*
    // methods related to server startup and
     */

    /**
     *  Starts a server socket on the default port.
     */
    private void startupServer() {
        serverStartupTask = new ServerStartupTask(DEFAULT_PORT);
        serverStartupTask.setOnSucceeded(this::serverStarted);
        serverStartupTask.setOnFailed(this::serverFailed);

        Thread thread = new Thread(serverStartupTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void serverStarted(WorkerStateEvent workerStateEvent) {
        try {
            serverSocket = serverStartupTask.get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        chatArea.appendText(String.format(
                "%s: Server started locally on port %s. Listening for incoming connections.\n",
                getTimeStamp(),
                serverSocket.getLocalPort()
        ));

        Server server = new Server(this, serverSocket);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
    }

    private void serverFailed(WorkerStateEvent workerStateEvent) {
        while (noOfRetries < 5) {
            noOfRetries++;
            startupServer();
        }
    }

    // called by background thread
    public void acceptConnection(Socket socket) {
        ObjectOutputStream oos;

        try (socket) {
            oos = new ObjectOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // only accept one active session at a time
        if (currentSessionSocket != null) {
            try {
                oos.writeObject(Message.DECLINED);
                oos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        String inetAddress = socket.getInetAddress().toString();
        Platform.runLater(() -> {
            var result = new Alert(Alert.AlertType.CONFIRMATION,
                    String.format("Accept connection from %s?", inetAddress))
                    .showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                currentSessionSocket = socket;
            }
        });
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
            case CANCEL -> cancelSession();
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

    private void cancelSession() {
        outgoingConnectionTask.cancel();

        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
    }

    private void stopSession() {
        if (currentSessionSocket == null) {
            return;
        }

        try {
            currentSessionSocket.close();
        } catch (IOException e) {
            // ignore;
        } finally {
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

        mainButton.setText("Stop session");
        mainButton.setDisable(false);
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
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore;
            }
        }

        if (currentSessionSocket != null) {
            try {
                currentSessionSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }





    /*
    // Other methods
     */

    /**
     * Convenience method for creating timestamps.
     */
    private String getTimeStamp() {
        return sdf.format(new Timestamp(System.currentTimeMillis()));
    }

}
