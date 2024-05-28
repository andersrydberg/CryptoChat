import javafx.concurrent.Task;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * A Runnable tasked with running a server that listens to the given port.
 * Runs until cancelled or until an IOException occurs.
 */
public class Server implements Runnable {
    private final Model model;
    private final int port;
    private boolean active = true;

    public Server(Model model, int port) {
        this.model = model;
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            // set timeout so thread does not block indefinitely
            serverSocket.setSoTimeout(2000);

            model.serverStarted();

            while (active) {
                try {
                    Socket socket = serverSocket.accept();
                    tryConnection(socket);

                } catch (SocketTimeoutException e) {
                    // ignore

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
            model.serverError();
        }
    }

    /**
     * Checks whether to accept or decline starting a chat session for the incoming connection.
     * If there is no active session ongoing, prompts the user for action.
     * @param socket the socket opened for the incoming connection
     */
    private void tryConnection(Socket socket) {
        if (model.hasOngoingChatSession()) {
            declineConnection(socket);
            return;
        }

        Task<Boolean> confirmation = model.promptUserForConfirmation(socket);
        try {
            if (confirmation.get()) {
                acceptConnection(socket);
            } else {
                declineConnection(socket);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            declineConnection(socket);
        }
    }

    /**
     * Starts a new ChatSession on a dedicated thread charged with accepting the invite.
     * @param socket the socket opened for the incoming connection
     */
    private void acceptConnection(Socket socket) {
        ChatSession toBeAccepted = new ChatSession(socket, model, Command.ACCEPTED);

        Thread thread = new Thread(toBeAccepted);
        //thread.setDaemon(true);
        thread.start();
    }

    /**
     * Starts a new ChatSession on a dedicated thread charged with declining the invite.
     * @param socket the socket opened for the incoming connection
     */
    private void declineConnection(Socket socket) {
        ChatSession toBeDeclined = new ChatSession(socket, model, Command.DECLINED);

        Thread thread = new Thread(toBeDeclined);
        //thread.setDaemon(true);
        thread.start();
    }

    public void deactivate() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }
}
