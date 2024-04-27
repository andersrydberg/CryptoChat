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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;


public class ChatController {

    private static final String NO_SESSION_MSG = "No ongoing session. Enter the IP address of your contact and press Start to start a session.";
    private static final int DEFAULT_PORT = 27119;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

    private ExecutorService threadPool;

    private ServerStartupTask serverStartupTask;
    private ServerSocket serverSocket;
    private Socket currentSessionSocket;
    private OutgoingConnectionTask outgoingConnectionTask;

    private ButtonState buttonState = ButtonState.START;

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
    private void initialize() {
        threadPool = Executors.newFixedThreadPool(10);
        startupServer(); // delete this when initBackend finished
        initBackend();
    }

    //
    private void initBackend() {
        Backend backend = new Backend(this);

        Thread thread = new Thread(backend);
        thread.setDaemon(true);
        thread.start();
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
        // display: could not start server, cannot receive incoming requests
    }

    // called by background thread
    public void tryConnection(Socket socket) {
        ObjectOutputStream oos;

        try (socket) {
            oos = new ObjectOutputStream(socket.getOutputStream());

            // only accept one active session at a time
            if (currentSessionSocket != null) {
                oos.writeObject(Message.DECLINED);
                oos.flush();
                socket.close();
                return;
            }

            // prompt user for confirmation
            String inetAddress = socket.getInetAddress().toString();
            var confirm = new GetConfirmationFromUserTask(inetAddress);
            Platform.runLater(confirm);

            try {
                if (confirm.get()) {
                    currentSessionSocket = socket;
                    oos.writeObject(Message.ACCEPTED);
                    oos.flush();
                    // start new task for interaction
                } else {
                    oos.writeObject(Message.DECLINED);
                    oos.flush();
                    socket.close();
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                socket.close();
            }


        } catch (IOException e) {
            e.printStackTrace();
        }

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

    public boolean hasOngoingSession() {
        return currentSessionSocket != null;
    }

    public void receiveSocket(Socket socket) {
        currentSessionSocket = socket;
    }
}
