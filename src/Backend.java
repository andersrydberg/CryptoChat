import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Backend {
    private static final int DEFAULT_PORT = 27119;

    private final ChatController chatController;
    private Server server;
    private Socket ongoingSession;

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
        logMessage("Error on server startup. Trying again in 1 sec...");
        try {
            Thread.sleep(1000);
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
            closeConnection(socket);
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
                ongoingSession = socket;
                logMessage("connection with ... established");
            } else {
                closeConnection(socket);
                logMessage("request from ... denied");
            }
        } catch (Exception e) {
            closeConnection(socket);
            logMessage("could not establish connection to ...");
        }
    }

    private void closeConnection(Socket socket) {
        try (socket) {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(Message.DECLINED);
            oos.flush();
        } catch (IOException e) {
            // ignore
        }
    }

    // methods for reacting to user input and events triggered subsequently

    public void outgoingConnection(String address) {
        if (ongoingSession != null) {
            throw new RuntimeException("Session already ongoing. Send button should be inactivated.");
        }

        OutgoingConnectionTask outgoingConnectionTask = new OutgoingConnectionTask(this, address, DEFAULT_PORT);

        Thread thread = new Thread(outgoingConnectionTask);
        thread.setDaemon(true);
        thread.start();
    }

    public void receiveSocket(Socket socket) {
        ongoingSession = socket;
        logMessage("outgoing connection established with ...");
        Platform.runLater(chatController::outgoingConnectionEstablished);
    }

    public void outgoingConnectionError() {
        logMessage("could not establish an outgoing connection with ...");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }


    public void closeSession() {
        if (ongoingSession == null) {
            throw new RuntimeException("No ongoing session. Cancel/Stop button should not be active");
        }

        Socket sessionToClose = ongoingSession;
        ongoingSession = null;

        var closeTask = new Runnable() {
            @Override
            public void run() {
                try {
                    sessionToClose.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        };

        Platform.runLater(closeTask);
        logMessage("Session ... closed");
    }




    // other methods

    private void logMessage(String message) {
        Platform.runLater(() -> chatController.appendToChat(message));
    }
}
