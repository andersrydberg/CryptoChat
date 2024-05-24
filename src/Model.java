import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.net.Socket;

/**
 * Manages the data and background threads of the application. Receives input from the controller
 * (upon user interaction) as well as the background threads (e.g. in response to network events)
 */
public class Model {
    private static final int DEFAULT_PORT = 27119;
    private final Controller controller;
    private Server server;
    private OutgoingConnection outgoingConnection;
    private ChatSession activeChatSession;

    public Model(Controller controller) {
        this.controller = controller;
    }



    /*
    // methods called at startup
     */

    public void start() {
        startServer();
    }

    /**
     * Starts the server on a dedicated background thread.
     */
    private void startServer() {
        server = new Server(this, DEFAULT_PORT);

        Thread thread = new Thread(server);
        thread.start();
    }


    /*
    // methods called by Server in response to server events
     */

    /**
     * Called when server has been started successfully.
     */
    public void serverStarted() {
        displayMessage("Server started. Listening on port " + DEFAULT_PORT);
    }

    /**
     * Called when there is an error upon server startup. Restarts server after 5 seconds.
     */
    public void serverStartupError() {
        displayMessage("Error on server startup. Trying again in 5 sec...");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            return;
        }
        startServer();
    }

    /*
    // methods for reacting to user input and events triggered subsequently
     */

    /**
     * Called by the controller when the user initiates a connection to a remote host.
     * Starts the outgoing connection on a separate thread.
     * @param address the address to connect to
     */
    public void connectTo(String address) {
        if (outgoingConnection != null || activeChatSession != null) {
            System.err.println("Session already ongoing. Start button should be inactivated.");
            outgoingConnectionError(address);
        }

        outgoingConnection = new OutgoingConnection(this, address, DEFAULT_PORT);
        Thread thread = new Thread(outgoingConnection);
        thread.start();
    }

    // methods called by OutgoingConnection

    /**
     * Called when the outgoing connection generates an error
     */
    public void outgoingConnectionError(String address) {
        outgoingConnection = null;
        displayMessage("Could not establish an outgoing connection with " + address);
        controller.outgoingConnectionFailed();
    }

    /**
     * Called when the remote host has accepted the connection. Starts a chat session on a new thread.
     */
    public void outgoingConnectionEstablished(Socket socket) {
        outgoingConnection = null;

        activeChatSession = new ChatSession(socket, this);
        Thread thread = new Thread(activeChatSession);
        thread.start();
    }

    /**
     * Called when the remote host has refused the connection
     */
    public void outgoingConnectionRefused(String address) {
        activeChatSession = null;
        displayMessage("Remote host " + address + " has refused the connection");
        controller.outgoingConnectionFailed();
    }

    // methods called by ChatSession
    public void sessionStarted(ChatSession chatSession, String ownPublicKey, String othersPublicKey) {
        activeChatSession = chatSession;
        displayMessage("New session started");
        controller.sessionStarted(ownPublicKey, othersPublicKey, chatSession.getRemoteAddress());
    }

    public void cancelOutgoingConnection() {
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
        if (activeChatSession != null) {
            activeChatSession.cancel();
            activeChatSession = null;
        }
    }

    public void sessionEnding() {
        controller.sessionEnded();
    }

    // other methods

    private void displayMessage(String message) {
        controller.displayMessage(message);
    }

    public void sendMessage(String message) {
        activeChatSession.writeToRemoteHost(message);
    }

    public void receiveMessage(String message) {
        displayMessage(message);
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

    public void shutdown() {
        if (server != null) {
            server.deactivate();
        }
        cancelOutgoingConnection();
    }
}
