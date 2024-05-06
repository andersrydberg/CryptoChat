import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Backend {
    private final ChatController chatController;
    private Server2 server;
    private Socket ongoingSession;

    public Backend(ChatController chatController) {
        this.chatController = chatController;
    }

    public void start() {
        startServer();
    }

    private void startServer() {
        server = new Server2(this);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
    }


    // methods for reacting to server events

    public void serverStarted() {
        logMessage("Server started...");
    }

    public void serverStartupError() {
        if (server != null) {
            server.deactivate();
        }
        startServer();
    }

    /**
     * check if there is an ongoing session, else prompt user
     *
     * @param socket
     */
    public void tryConnection(Socket socket) {

        if (ongoingSession != null) {
            closeConnection(socket);
            logMessage("request from ... rejected: ongoing session");
            return;
        }

        // prompt user...
        final String inetAddress = socket.getInetAddress().toString();
        var confirm = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
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

    // methods for reacting to user input


    // other methods

    private void logMessage(String message) {
        Platform.runLater(() -> {
            chatController.appendToChat(message);
        });
    }

}
