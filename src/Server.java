import javafx.concurrent.Task;

import java.net.ServerSocket;
import java.net.Socket;


public class Server extends Task<Void> {

    private final ChatController chatController;
    private final ServerSocket serverSocket;

    public Server(ChatController chatController, ServerSocket serverSocket) {
        this.chatController = chatController;
        this.serverSocket = serverSocket;
    }


    @Override
    protected Void call() throws Exception {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                chatController.acceptConnection(socket);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        return null;
    }
}
