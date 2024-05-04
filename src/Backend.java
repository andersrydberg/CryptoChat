import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Backend implements Runnable {
    private final ChatController chatController;
    private Server2 server;
    private Socket ongoingSession;

    public Backend(ChatController chatController) {
        this.chatController = chatController;
    }

    @Override
    public void run() {
        // start server
        server = new Server2(this);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
    }


    // methods for reacting to server events
    public void serverStartupError() {
    }

    /**
     * check if there is an ongoing session, else prompt user
     *
     * @param socket
     */
    public void tryConnection(Socket socket) {

        if (ongoingSession != null) {
            closeConnection(socket);
            // log: request from ... rejected: ongoing session
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
                // log: connection with ... established
            } else {
                closeConnection(socket);
                // log: request from ... denied
            }
        } catch (Exception e) {
            closeConnection(socket);
            // log: could not establish connection to ...
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
}
