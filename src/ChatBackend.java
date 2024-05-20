import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.net.Socket;

public class ChatBackend {
    private static final int DEFAULT_PORT = 27119;
    private final ChatController chatController;
    private Server server;
    private OutgoingConnection outgoingConnection;
    private ChatSession activeChatSession;

    public ChatBackend(ChatController chatController) {
        this.chatController = chatController;
    }

    public void start() {
        startServer();
    }

    private void startServer() {
        server = new Server(this, DEFAULT_PORT);

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


    // methods for reacting to user input and events triggered subsequently

    /**
     * Called when the user initiates a connection to a remote host
     * @param address
     */
    public void connectTo(String address) {
        System.err.println(Thread.currentThread().getName() + " initializeOutgoingConnection");

        if (outgoingConnection != null || activeChatSession != null) {
            throw new RuntimeException("Session already ongoing. Start button should be inactivated.");
        }

        outgoingConnection = new OutgoingConnection(this, address, DEFAULT_PORT);
        Thread thread = new Thread(outgoingConnection);
        thread.setDaemon(true);
        thread.setName("Outgoing Connection Thread");
        thread.start();
    }

    public void outgoingConnectionError() {
        System.err.println(Thread.currentThread().getName() + " outgoingConnectionError");

        outgoingConnection = null;
        logMessage("could not establish an outgoing connection with ...");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }

    public void outgoingConnectionEstablished(Socket socket) {
        outgoingConnection = null;

        activeChatSession = new ChatSession(socket, this);
        Thread thread = new Thread(activeChatSession);
        thread.setDaemon(true);
        thread.setName("Chat Session From Client Thread");
        thread.start();
    }

    public void outgoingConnectionRefused() {
        System.err.println(Thread.currentThread().getName() + " outgoingConnectionRefused");

        activeChatSession = null;
        logMessage("remote host rejected connection");
        Platform.runLater(chatController::outgoingConnectionFailed);
    }

    public void sessionStarted(ChatSession chatSession, String ownPublicKey, String othersPublicKey) {
        activeChatSession = chatSession;
        logMessage("New session started");
        Platform.runLater(() -> chatController.sessionStarted(ownPublicKey, othersPublicKey));
    }

    public void cancelOutgoingConnection() {
        System.err.println(Thread.currentThread().getName() + " cancelOutgoingConnection");

        if (outgoingConnection != null) {
            outgoingConnection.cancel();
            outgoingConnection = null;
        }
        if (activeChatSession != null) {
            activeChatSession.cancel();
            activeChatSession = null;
        }
    }

    public void stopActiveSession() {
        System.err.println(Thread.currentThread().getName() + " stopCurrentSession");

        if (activeChatSession != null) {
            activeChatSession.cancel();
            activeChatSession = null;
        }
    }

    public void sessionEnding() {
    }

    // other methods

    private void logMessage(String message) {
        Platform.runLater(() -> chatController.appendToChat(message));
    }

/*

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

*/

    public void sendMessage(String message) {
        activeChatSession.writeToRemoteHost(message);
    }

    public void receiveMessage(String message) {
        logMessage(message);
    }

    public boolean hasOngoingChatSession() {
        return activeChatSession != null;
    }

    public Task<Boolean> promptUserForConfirmation(Socket socket) {
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
        return confirm;
    }
}
