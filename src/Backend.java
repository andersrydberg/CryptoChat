import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;

public class Backend {
    private static final int DEFAULT_PORT = 27119;

    private final ChatController chatController;
    private Server server;
    private OngoingSession ongoingSession;

    public Backend(ChatController chatController) {
        this.chatController = chatController;
    }

    public void start() {
        startServer();
    }

    private void startServer() {
        server = new Server(this);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
    }


    // methods for reacting to server events

    public void serverStarted() {
        logMessage("Server started...");
    }

    public void serverStartupError() {
        logMessage("Error on server startup. Trying again in 5 sec...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            return;
        }
        startServer();
    }

    /**
     * check if there is an ongoing session, else prompt user
     *
     * @param socket
     */
    public void incomingConnection(Socket socket) {

        if (ongoingSession != null) {
            sendDecline(socket);
            logMessage("request from ... rejected: ongoing session");
            return;
        }

        // prompt user...
        final String inetAddress = socket.getInetAddress().toString();
        var confirm = new Task<Boolean>() {
            @Override
            protected Boolean call() {
                var result = new Alert(Alert.AlertType.CONFIRMATION,
                        String.format("Accept connection from %s?", inetAddress))
                        .showAndWait();
                return (result.isPresent() && result.get() == ButtonType.OK);
            }
        };

        Platform.runLater(confirm);

        try {
            if (confirm.get()) {
                sendAccept(socket);
                ongoingSession = new OngoingSession(socket, this);
                logMessage("connection with ... established");
                Platform.runLater(chatController::outgoingConnectionEstablished);
            } else {
                sendDecline(socket);
                logMessage("request from ... denied");
                Platform.runLater(chatController::outgoingConnectionFailed);
            }
        } catch (Exception e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
            closeSocket(socket);
            logMessage("could not establish connection to ...");
        }
    }


    // methods for reacting to user input and events triggered subsequently

    public void initializeOutgoingConnection(String address) {
        if (ongoingSession != null) {
            throw new RuntimeException("Session already ongoing. Send button should be inactivated.");
        }

        OutgoingConnectionTask outgoingConnectionTask = new OutgoingConnectionTask(this, address, DEFAULT_PORT);

        Thread thread = new Thread(outgoingConnectionTask);
        thread.setDaemon(true);
        thread.start();
    }

    public void receiveSocket(Socket socket) {
        ongoingSession = new OngoingSession(socket, this);
        logMessage("outgoing connection established with ...");
        Platform.runLater(chatController::outgoingConnectionEstablished);
    }

    public void outgoingConnectionError() {
        logMessage("could not establish an outgoing connection with ...");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }

    public void outgoingConnectionRefused() {
        logMessage("remote host rejected connection");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }


    private void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }

    public void cancelOutgoingConnection() {
        ongoingSession.cancel();
        ongoingSession = null;
    }


    // other methods

    private void logMessage(String message) {
        Platform.runLater(() -> chatController.appendToChat(message));
    }

    private void sendMessageToRemoteHost(Socket socket, Message message) {
        var sendTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(message);
                    oos.flush();
                } catch (IOException e) {
                    closeSocket(socket);
                }
            }
        };

        Thread thread = new Thread(sendTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void sendDecline(Socket socket) {
        sendMessageToRemoteHost(socket, Message.DECLINED);
        closeSocket(socket);
    }

    private void sendAccept(Socket socket) {
        sendMessageToRemoteHost(socket, Message.ACCEPTED);
    }

}
