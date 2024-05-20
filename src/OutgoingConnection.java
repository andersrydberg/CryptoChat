
import java.io.IOException;
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
    private boolean cancelled = false;

    public OutgoingConnection(ChatBackend chatBackend, String host, int port) {
        this.chatBackend = chatBackend;
        this.host = host;
        this.port = port;
    }

    /**
     *
     */
    @Override
    public void run() {
        Socket socket = new Socket();

        try {
            InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 1");

            while (!cancelled) {
                try {
                    socket.connect(inetSocketAddress, 5000);
                    chatBackend.outgoingConnectionEstablished(socket);
                    break;
                } catch (SocketTimeoutException e) {
                    // continue
                }
            }

            /*

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 2");

            //socket.setSoTimeout(1000);

            System.err.println(Thread.currentThread().getName() + " OutgoingConnection.run 3");

            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
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
                    System.err.println(e.getClass().toString() + ": " + e.getMessage());
                    chatBackend.outgoingConnectionError();
                    break;
                }
            }

             */

        } catch (IOException e) {
            System.err.println(e.getClass().toString() + ": " + e.getMessage());
            chatBackend.outgoingConnectionError();
        }
    }


    public void cancel() {
        cancelled = true;
    }
}
