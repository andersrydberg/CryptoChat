
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 */
public class OutgoingConnection implements Runnable {

    private final Model model;
    private final String host;
    private final int port;
    private boolean cancelled = false;

    public OutgoingConnection(Model model, String host, int port) {
        this.model = model;
        this.host = host;
        this.port = port;
    }

    /**
     *
     */
    @Override
    public void run() {
        Socket socket = new Socket();
        InetSocketAddress inetSocketAddress;

        try {
            inetSocketAddress = new InetSocketAddress(host, port);

            while (!cancelled) {
                try {
                    socket.connect(inetSocketAddress, 5000);
                    model.outgoingConnectionEstablished(socket);
                    break;
                } catch (SocketTimeoutException e) {
                    // continue
                }
            }
        } catch (IOException e) {
            model.outgoingConnectionRefused(host);
        }
    }


    public void cancel() {
        cancelled = true;
    }
}
