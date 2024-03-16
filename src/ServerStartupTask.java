import javafx.concurrent.Task;

import java.net.ServerSocket;

public class ServerStartupTask extends Task<ServerSocket> {

    private final int port;

    public ServerStartupTask(int port) {
        this.port = port;
    }

    @Override
    protected ServerSocket call() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return serverSocket;
        }
    }

}
