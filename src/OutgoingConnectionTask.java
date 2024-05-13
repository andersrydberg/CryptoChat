
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

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

            try {
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                Message message = (Message) ois.readObject();
                if (message.equals(Message.ACCEPTED)) {
                    backend.receiveSocket(socket);
                } else {
                    backend.outgoingConnectionRefused();
                }
            } catch (ClassCastException e) {
                System.err.println(Arrays.toString(e.getStackTrace()));
                // bad grammar
            } catch (Exception e) {
                System.err.println(Arrays.toString(e.getStackTrace()));
                backend.outgoingConnectionError();
            }

        } catch (IOException e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
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
