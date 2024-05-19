
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 */
public class OutgoingConnection implements Runnable {

    private final ChatBackend chatBackend;
    private final String host;
    private final int port;
    private final Socket socket;
    private boolean cancelled = false;

    public OutgoingConnection(ChatBackend chatBackend, String host, int port) {
        this.chatBackend = chatBackend;
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

            while (!cancelled) {
                try {
                    socket.connect(inetSocketAddress, 1000);
                    break;
                } catch (SocketTimeoutException e) {
                    // ignore
                }
            }

            socket.setSoTimeout(1000);

            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            while (!cancelled) {
                try {
                    Command command = (Command) ois.readObject();
                    if (command.equals(Command.ACCEPTED)) {
                        chatBackend.receiveSocket(socket);
                    } else {
                        chatBackend.outgoingConnectionRefused();
                    }
                    break;
                } catch (ClassCastException e) {
                    // bad grammar
                    break;
                } catch (SocketTimeoutException e) {
                    // continue
                } catch (Exception e) {
                    chatBackend.outgoingConnectionError();
                    break;
                }
            }
        } catch (IOException e) {
            chatBackend.outgoingConnectionError();
        }
    }


    public void cancel() {
        cancelled = true;
    }
}
