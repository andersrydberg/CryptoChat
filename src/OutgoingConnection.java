
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * A Runnable charged with establishing an outgoing connecting to the specified host/port.
 * Runs until a connection has been established, i.e. the remote server has run
 * "serverSocket.accept()", or any kind of error has occurred, or until cancelled.
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

            if (cancelled) {
                model.outgoingConnectionEnded("You have cancelled the outgoing connection to " + socket.getInetAddress().toString() + ".");
            }

        } catch (IOException e) {
            model.outgoingConnectionEnded("Could not establish an outgoing connection to " + socket.getInetAddress().toString());
        }
    }


    public void cancel() {
        cancelled = true;
    }
}
