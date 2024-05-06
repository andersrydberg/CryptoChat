
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 */
public class OutgoingConnectionTask implements Runnable {

    private final Backend backend;
    private final String host;
    private final int port;
    private final Socket socket;
    public OutgoingConnectionTask(Backend backend, String host, int port) {
        this.backend = backend;
        this.host = host;
        this.port = port;
        this.socket = new Socket();
    }

    /**
     *
     */
    @Override
    public void run() {
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
            socket.connect(inetSocketAddress);

            // get the streams, but don't use them (check for exceptions)
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            backend.receiveSocket(socket);
        } catch (IOException e) {
            backend.outgoingConnectionError();
        }
    }

    public String getHost() {
        return host;
    }

    public void shutdown() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
