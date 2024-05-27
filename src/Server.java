import javafx.concurrent.Task;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

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

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(2000);
        } catch (IOException e) {
            e.printStackTrace();
            model.serverStartupError();
            return;
        }

        model.serverStarted();

        while (active) {
            try {
                Socket socket = serverSocket.accept();
                tryConnection(socket);
                //chatBackend.tryIncomingConnection(socket);

            } catch (SocketTimeoutException e) {
                // ignore
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignore
        }

    }

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
        }
    }

    private void acceptConnection(Socket socket) {
        ChatSession toBeAccepted = new ChatSession(socket, model, Command.ACCEPTED);

        Thread thread = new Thread(toBeAccepted);
        thread.setDaemon(true);
        thread.start();
    }

    private void declineConnection(Socket socket) {
        ChatSession toBeDeclined = new ChatSession(socket, model, Command.DECLINED);

        Thread thread = new Thread(toBeDeclined);
        thread.setDaemon(true);
        thread.start();
    }

    public void deactivate() {
        active = false;
    }
}
