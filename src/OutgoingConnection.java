
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
    private boolean cancelled;

    public OutgoingConnection(ChatBackend chatBackend, String host, int port) {
        this.chatBackend = chatBackend;
        this.host = host;
        this.port = port;
        this.socket = new Socket();
        cancelled = false;
    }

    /**
     *
     */
    @Override
    public void run() {
        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 1");

            while (!cancelled) {
                try {
                    socket.connect(inetSocketAddress, 5000);
                    break;
                } catch (SocketTimeoutException e) {
                    // continue
                }
            }

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 2");

            //socket.setSoTimeout(1000);

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 3");

            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 4");

            while (!cancelled) {
                try {
                    Command command = (Command) ois.readObject();
                    if (command.equals(Command.ACCEPTED)) {
                        chatBackend.receiveSocket(socket);
                    } else {
                        chatBackend.outgoingConnectionRefused();
                    }
                    break;
                } catch (SocketTimeoutException e) {
                    // continue
                } catch (ClassCastException e) {
                    // bad grammar
                    break;
                } catch (Exception e) {
                    chatBackend.outgoingConnectionError();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println(e.getClass().toString() + ": " + e.getMessage());
            chatBackend.outgoingConnectionError();
        }
    }


    public void cancel() {
        cancelled = true;
    }
}
