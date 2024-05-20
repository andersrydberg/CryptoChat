import javafx.concurrent.Task;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

public class Server implements Runnable {
    private final ChatBackend chatBackend;
    private final int port;
    private ServerSocket serverSocket;
    private boolean active = true;

    public Server(ChatBackend chatBackend, int port) {
        this.chatBackend = chatBackend;
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
            chatBackend.serverStartupError();
            return;
        }

        chatBackend.serverStarted();

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

    }

    private void tryConnection(Socket socket) {
        if (chatBackend.hasOngoingChatSession()) {
            declineConnection(socket);
            return;
        }

        Task<Boolean> confirmation = chatBackend.promptUserForConfirmation(socket);
        try {
            if (confirmation.get()) {
                acceptConnection(socket);
            } else {
                declineConnection(socket);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void acceptConnection(Socket socket) {
        ChatSession toBeAccepted = new ChatSession(socket, chatBackend, Command.ACCEPTED);

        Thread thread = new Thread(toBeAccepted);
        thread.setDaemon(true);
        thread.start();
    }

    private void declineConnection(Socket socket) {
        ChatSession toBeDeclined = new ChatSession(socket, chatBackend, Command.DECLINED);

        Thread thread = new Thread(toBeDeclined);
        thread.setDaemon(true);
        thread.start();
    }

    public void deactivate() {
        active = false;
    }
}
