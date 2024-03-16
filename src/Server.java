import javafx.concurrent.Task;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Server extends Task<Void> {

    private final ChatController chatController;
    private final ServerSocket serverSocket;

    public Server(ChatController chatController, ServerSocket serverSocket) {
        this.chatController = chatController;
        this.serverSocket = serverSocket;
    }


    @Override
    protected Void call() throws Exception {
        // set timeout to 0.5 seconds so that we can check for user cancelling
        serverSocket.setSoTimeout(500);

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                chatController.acceptConnection(socket);
                if (isCancelled()) {
                    break;
                }

            } catch (SocketTimeoutException e) {
                if (isCancelled()) {
                    break;
                }
            }
        }
        return null;
    }
}
