import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutionException;

public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final int DEFAULT_PORT = 27119;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private ServerStartupTask serverStartupTask;
    private ServerSocket serverSocket;
    private Socket currentSessionSocket;
    private ButtonState buttonState = ButtonState.START;
    private ConnectionTask connectionTask;

    private int noOfRetries = 0;

    public TextField ipField;
    public Button mainButton;
    public Text displayText;
    public VBox publicKeyBox;
    public TextField ownKeyField;
    public TextField contactPublicKey;
    public TextArea chatArea;
    public TextField chatInputField;

    /**
     * Runs after the controller is constructed.
     */
    @FXML
    public void initialize() {
        startupServer();
    }


    // methods related to server startup and

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
                "%s: Server started locally on port %s. Listening for incoming connections.",
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
        // only accept one active session at a time
        if (currentSessionSocket == null) {
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
    }


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

        connectionTask = new ConnectionTask(ipField.getText().trim(), DEFAULT_PORT);
        connectionTask.setOnSucceeded(this::connectionSucceededHandler);
        connectionTask.setOnFailed(this::connectionFailedHandler);

        buttonState = ButtonState.CANCEL;

        Thread thread = new Thread(connectionTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void cancelSession() {
        connectionTask.cancel();

        ipField.setEditable(true);
        mainButton.setText("Start session");
        buttonState = ButtonState.START;
    }

    private void stopSession() {
        if (currentSessionSocket == null) {
            return;
        }


    }

    private void connectionSucceededHandler(WorkerStateEvent workerStateEvent) {
        try {
            currentSessionSocket = connectionTask.get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException();
        }

        mainButton.setText("Stop session");
        mainButton.setDisable(false);
        buttonState = ButtonState.STOP;
    }

    private void connectionFailedHandler(WorkerStateEvent workerStateEvent) {

    }

    public void sendMessageHandler(ActionEvent event) {

    }


    /**
     * Closes the server socket so that any associated threads die.
     */
    public void shutdown() {
        // TODO
    }

    /**
     * Convenience method for creating timestamps
     */
    private String getTimeStamp() {
        return sdf.format(new Timestamp(System.currentTimeMillis()));
    }

}
