import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ChatBackend {
    private static final int DEFAULT_PORT = 27119;
    private final ChatController chatController;
    private Server server;
    private ChatSession ongoingChatSession;
    private OutgoingConnection outgoingConnection;  // field so accessible if needs to be cancelled

    public ChatBackend(ChatController chatController) {
        this.chatController = chatController;
    }

    public void start() {
        startServer();
    }

    private void startServer() {
        server = new Server(this);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.setName("Server Thread");
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
     * Called when there is an incoming connection from a remote host
     * check if there is an ongoing session, else prompt user
     * Main flow of execution runs in the Server thread, so should
     * probably be placed in that class
     *
     * @param socket
     */
    public void tryIncomingConnection(Socket socket) {
        System.err.println(Thread.currentThread().getName() + " tryIncomingConnection 1");

        if (ongoingChatSession != null) {
            sendDecline(socket);
            logMessage("request from ... rejected: ongoing session");
            return;
        }

        // prompt user...
        final String inetAddress =
                socket.getInetAddress().toString()
                + " on port " + socket.getPort()
                + " (local port is "
                + socket.getLocalPort() + ")";
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

        System.err.println(Thread.currentThread().getName() + " tryIncomingConnection 2");

        try {
            if (confirm.get()) {
                sendAccept(socket);

                ongoingChatSession = new ChatSession(socket, this);
                Thread thread = new Thread(ongoingChatSession);
                thread.setDaemon(true);
                thread.setName("Chat Session From Incoming Thread");
                thread.start();
            } else {
                sendDecline(socket);
                logMessage("request from ... denied");
                Platform.runLater(chatController::outgoingConnectionFailed);
            }
        } catch (Exception e) {
            closeSocket(socket);
            logMessage("could not establish connection to ...");
        }
    }


    // methods for reacting to user input and events triggered subsequently

    /**
     * Called when the user initiates a connection to a remote host
     * @param address
     */
    public void initializeOutgoingConnection(String address) {
        System.err.println(Thread.currentThread().getName() + " initializeOutgoingConnection");

        if (ongoingChatSession != null) {
            throw new RuntimeException("Session already ongoing. Start button should be inactivated.");
        }

        outgoingConnection = new OutgoingConnection(this, address, DEFAULT_PORT);

        Thread thread = new Thread(outgoingConnection);
        thread.setDaemon(true);
        thread.setName("Outgoing Connection Thread");
        thread.start();
    }

    public void receiveSocket(Socket socket) {
        System.err.println(Thread.currentThread().getName() + " receiveSocket");

        ongoingChatSession = new ChatSession(socket, this);
        Thread thread = new Thread(ongoingChatSession);
        thread.setDaemon(true);
        thread.setName("Chat Session From Client Thread");
        thread.start();
    }

    public void outgoingConnectionError() {
        System.err.println(Thread.currentThread().getName() + " outgoingConnectionError");

        outgoingConnection = null;
        logMessage("could not establish an outgoing connection with ...");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }

    public void outgoingConnectionRefused() {
        System.err.println(Thread.currentThread().getName() + " outgoingConnectionRefused");

        outgoingConnection = null;
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
        System.err.println(Thread.currentThread().getName() + " cancelOutgoingConnection");


        if (outgoingConnection != null) {
            outgoingConnection.cancel();
        }
        outgoingConnection = null;
    }

    public void stopCurrentSession() {
        System.err.println(Thread.currentThread().getName() + " stopCurrentSession");

        if (ongoingChatSession != null) {
            ongoingChatSession.cancel();
        }
        ongoingChatSession = null;
    }

    // other methods

    private void logMessage(String message) {
        Platform.runLater(() -> chatController.appendToChat(message));
    }

    private void sendCommand(Socket socket, Command command) {
        var sendTask = new Runnable() {
            @Override
            public void run() {
                try {
                    System.err.println(Thread.currentThread().getName() + " sendCommand 1");
                    ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                    oos.writeObject(command);
                    oos.flush();
                    System.err.println(Thread.currentThread().getName() + " sendCommand 2");
                } catch (IOException e) {
                    closeSocket(socket);
                }
            }
        };

        Thread thread = new Thread(sendTask);
        thread.setDaemon(true);
        thread.setName("Send Command Thread");
        thread.start();
    }

    private void sendDecline(Socket socket) {
        System.err.println(Thread.currentThread().getName() + " sendDecline");

        sendCommand(socket, Command.DECLINED);
        closeSocket(socket);
    }

    private void sendAccept(Socket socket) {
        System.err.println(Thread.currentThread().getName() + " sendAccept");

        sendCommand(socket, Command.ACCEPTED);
    }


    public void sendMessage(String message) {
        ongoingChatSession.writeToRemoteHost(message);
    }

    public void receiveKeys(String ownPublicKey, String othersPublicKey) {
        logMessage("New session started");
        Platform.runLater(() -> chatController.outgoingConnectionEstablished(ownPublicKey, othersPublicKey));
    }

    public void receiveMessage(String message) {
        logMessage(message);
    }
}
