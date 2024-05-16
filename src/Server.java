import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Server implements Runnable {


    private static final int DEFAULT_PORT = 27119;
    private final ChatBackend chatBackend;
    private ServerSocket serverSocket;
    private boolean active = true;

    public Server(ChatBackend chatBackend) {
        this.chatBackend = chatBackend;
    }

    @Override
    public void run() {

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT);
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
                chatBackend.tryIncomingConnection(socket);

            } catch (SocketTimeoutException e) {
                // ignore
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

    }

    public void deactivate() {
        active = false;
    }
}
