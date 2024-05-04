import java.io.IOException;
import java.net.Socket;

public class Backend implements Runnable {
    private final ChatController chatController;
    private Server2 server;
    private Socket ongoingSession;

    public Backend(ChatController chatController) {
        this.chatController = chatController;
    }

    @Override
    public void run() {
        // start server
        server = new Server2(this);

        Thread thread = new Thread(server);
        thread.setDaemon(true);
        thread.start();
    }


    // methods for reacting to server events
    public void serverStartupError() {
    }

    /**
     * check if there is an ongoing session, else prompt user
     * @param socket
     */
    public void tryConnection(Socket socket) {
        if (ongoingSession == null) {
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }

        // prompt user...
    }

    // methods for reacting to user input
}
