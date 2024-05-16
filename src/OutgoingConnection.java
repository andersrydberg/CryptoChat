
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Task assigned with connecting to the specified host on default port ... Uses a stream socket.
 */
public class OutgoingConnection implements Runnable {

    private final ChatBackend chatBackend;
    private final String host;
    private final int port;
    private final Socket socket;
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
            socket.connect(inetSocketAddress);

            try {
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
                Command command = (Command) ois.readObject();
                if (command.equals(Command.ACCEPTED)) {
                    chatBackend.receiveSocket(socket);
                } else {
                    chatBackend.outgoingConnectionRefused();
                }
            } catch (ClassCastException e) {
                // bad grammar
            } catch (Exception e) {
                chatBackend.outgoingConnectionError();
            }

        } catch (IOException e) {
            chatBackend.outgoingConnectionError();
        }
    }

    public String getHost() {
        return host;
    }

    public void cancel() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
