import javafx.concurrent.Task;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 */
public class OutgoingConnectionTask extends Task<Socket> {

    private final String host;
    private final int port;
    public OutgoingConnectionTask(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * @return the stream socket, if connection is successful
     * @throws Exception if the connection failed for some reason
     */
    @Override
    protected Socket call() throws Exception {
        Socket socket = new Socket();
        try (socket) {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
            socket.connect(inetSocketAddress);

            // get the streams, but don't use them (check for exceptions)
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        }
        return socket;
    }

    public String getHost() {
        return host;
    }
}
