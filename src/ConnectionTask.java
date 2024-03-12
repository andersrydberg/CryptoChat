import javafx.concurrent.Task;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 * TODO: should perhaps return the Socket?
 */
public class ConnectionTask extends Task<Void> {

    private final String host;
    private final int port;
    public ConnectionTask(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    protected Void call() throws Exception {
        Socket socket = new Socket();
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
            // set timeout to 3 seconds so that blocking calls will not block forever
            // e.g. if the remote host is unresponsive
            socket.connect(inetSocketAddress, 3000);
            socket.setSoTimeout(3000);

            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
            String address = socket.getInetAddress().toString();

            updateMessage("Connection established with " + address);

            while (true) {
                // TODO: communicate
                break;
            }
        } finally {
            socket.close();
        }
        return null;
    }
}
