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
     * Called when there is a server error. Restarts server after 5 seconds.
     */
    public void serverError() {
        displayMessage("Server error. Restarting server in 5 seconds...");

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // don't restart the server if the app is shutting down
        if (!server.isActive()) {
            return;
        }

        startServer();
    }

    /**
     * Called when there is an incoming connection. Prompts the user to accept or reject the connection.
     * @param socket the socket opened for the incoming connection
     * @return a Task object whose return value can be retrieved
     */
    public Task<Boolean> promptUserForConfirmation(Socket socket) {

        // first create the Task (it will have a return value when successfully executed)
        String inetAddress =
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
                return (result.isPresent() && result.get() == ButtonType.OK);   // true if user accepts
            }
        };

        // run the task on the JavaFx Application thread
        Platform.runLater(confirm);

        // return the task so its return value can be retrieved
        return confirm;
    }


    /*
    // methods called by the controller upon user interaction
     */

    /**
     * Called when the user initiates a connection to a remote host.
     * Starts the outgoing connection on a separate thread.
     * @param address the address to connect to
     */
    public void connectTo(String address) {
        if (outgoingConnection != null || activeChatSession != null) {
            System.err.println("Session already ongoing. Start button should be inactivated.");
            controller.sessionEnded();
        }

        outgoingConnection = new OutgoingConnection(this, address, DEFAULT_PORT);
        Thread thread = new Thread(outgoingConnection);
        thread.start();
    }

    /**
     * Called when the user wants to send a chat message.
     */
    public void sendMessage(String message) {
        activeChatSession.writeToRemoteHost(message);
    }

    /**
     * Called when the user wants to cancel an outgoing connection.
     */
    public void cancelOutgoingConnection() {
        if (outgoingConnection != null) {
            outgoingConnection.cancel();
        }
        // if a connection to the remote host has been established but no accept/decline command has been received
        stopActiveSession();
    }

    /**
     * Called when the user wants to end an active chat session.
     */
    public void stopActiveSession() {
        if (activeChatSession != null) {
            activeChatSession.cancel();
        }
    }


    /*
    // methods called by OutgoingConnection
     */

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
     * Called when a connection could not be established, or has been cancelled by the user.
     * @param message the status message to display to the user
     */
    public void outgoingConnectionEnded(String message) {
        outgoingConnection = null;
        displayMessage(message);
        controller.sessionEnded();
    }


    /*
    // methods called by ChatSession
    */

    /**
     * Called when an active chat session has been successfully initiated.
     * @param chatSession the ChatSession object
     * @param ownPublicKey the user's (digested) public key
     * @param othersPublicKey remote host's (digested) public key
     */
    public void sessionStarted(ChatSession chatSession, String ownPublicKey, String othersPublicKey) {
        outgoingConnection = null;
        activeChatSession = chatSession;
        displayMessage("New session started.");
        controller.sessionStarted(ownPublicKey, othersPublicKey, chatSession.getRemoteAddress());
    }

    /**
     * Called when an active chat session has been terminated for any reason.
     * @param message the status message to display to the user
     */
    public void sessionEnded(String message) {
        outgoingConnection = null;
        activeChatSession = null;
        displayMessage(message);
        controller.sessionEnded();
    }

    /**
     * Called when an incoming connection has been rejected (either because there is already an ongoing
     * session, or because the user has chosen to decline).
     * @param message the status message to display to the user
     */
    public void incomingConnectionDeclined(String message) {
        displayMessage(message);
    }

    /**
     * Called when a chat message has been read from the input stream.
     * @param message the chat message
     */
    public void readMessage(String message) {
        displayMessage(message);
    }


    /*
    // methods called by "writeTask" in ChatSession.writeToRemoteHost
     */

    /**
     * Called when a message has been successfully written to the output stream.
     * @param message the message that was written
     */
    public void wroteMessage(String message) {
        displayMessage("You: " + message);
    }

    /**
     * Called when there was an error writing to the output stream
     * @param message the message that was being written
     */
    public void errorWritingMessage(String message) {
        displayMessage("There was an error writing your message: " + message);
    }



    /*
    // other methods
     */

    /**
     * Convenience method for writing messages to the chat area.
     * @param message the message to be written
     */
    private void displayMessage(String message) {
        controller.displayMessage(message);
    }

    public boolean hasOngoingChatSession() {
        return activeChatSession != null;
    }

    /**
     * Called by the controller at shutdown.
     */
    public void shutdown() {
        if (server != null) {
            server.deactivate();
        }
        cancelOutgoingConnection();
    }
}
