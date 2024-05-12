import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class OngoingSession implements Runnable {

    private final Socket socket;
    private final Backend backend;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private boolean cancelled;

    public OngoingSession(Socket socket, Backend backend) {
        this.socket = socket;
        this.backend = backend;
        this.cancelled = false;

        try {
            socket.setSoTimeout(3000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
            this.ois = input;

            readFromClient();

        } catch (IOException e) {
            // ignore
        } finally {
            cancelled = true;
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void readFromClient() {
        while (!cancelled) {
            try {
                Message message = (Message) ois.readObject();

                if (message.equals(Message.DECLINED)) {
                    // remote host has closed
                    break;
                } else if (message.equals(Message.MESSAGE)) {
                    // read encrypted message
                } else {
                    // bad grammar
                    break;
                }

            } catch (SocketTimeoutException e) {
                // ignore
            } catch (ClassCastException e) {
                // bad grammar
                break;
            } catch (ClassNotFoundException e) {
                // should not occur
                System.err.println(e.getMessage());
                break;
            } catch (IOException e) {
                System.err.println(e.getMessage());
                break;
            }
        }
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}

