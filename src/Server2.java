import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Server2 implements Runnable {


    private static final int DEFAULT_PORT = 27119;
    private final Backend backend;
    private ServerSocket serverSocket;
    private boolean active = true;

    public Server2(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void run() {

        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT);
            serverSocket.setSoTimeout(2000);
        } catch (IOException e) {
            e.printStackTrace();
            backend.serverStartupError();
            return;
        }

        backend.serverStarted();

        while (active) {
            try {
                Socket socket = serverSocket.accept();
                backend.tryConnection(socket);

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
