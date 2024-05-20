import javax.crypto.SealedObject;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Thread runs for as long as the chat session is active.
 * Main purpose - read messages sent from client, delegate these to the Backend object
 */
public class ChatSession implements Runnable {

    private final Socket socket;
    private final ChatBackend chatBackend;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private boolean cancelled;
    private Cryptographer cryptographer;


    public ChatSession(Socket socket, ChatBackend chatBackend) {
        this.socket = socket;
        this.chatBackend = chatBackend;
        this.cancelled = false;
    }

    @Override
    public void run() {
        System.err.println(Thread.currentThread().getName() + " ChatSession.run 1");

        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            this.ois = ois;
            this.oos = oos;

            System.err.println(Thread.currentThread().getName() + " ChatSession.run 2");

            cryptographer = new Cryptographer();
            cryptographer.exchangeKeys(ois, oos);
            chatBackend.receiveKeys(cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());

            socket.setSoTimeout(1000);

            readFromClient();

        } catch (Exception e) {
            // ignore
        } finally {
            chatBackend.stopCurrentSession();
            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }


    private void readFromClient() throws Exception {
        System.err.println(Thread.currentThread().getName() + " ChatSession.readFromClient");

        while (!cancelled) {
            try {
                Command command = (Command) ois.readObject();

                if (command.equals(Command.DECLINED)) {
                    // remote host has closed
                    break;
                } else if (command.equals(Command.MESSAGE)) {
                    // read encrypted message
                    String message = cryptographer.decryptMessage((SealedObject) ois.readObject());
                    chatBackend.receiveMessage(message);
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

    public void writeToRemoteHost(String message) {
        WriteToRemoteHost writeTask = new WriteToRemoteHost(message);

        Thread thread = new Thread(writeTask);
        thread.setDaemon(true);
        thread.start();
    }

    public void cancel() {
        cancelled = true;
    }

    private class WriteToRemoteHost implements Runnable{
        private final String message;

        public WriteToRemoteHost(String message) {
            this.message = message;
        }

        @Override
        public void run() {
            try {
                oos.writeObject(cryptographer.encrypt(message));
                oos.flush();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

