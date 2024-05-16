import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/**
 * Thread runs for as long as the chat session is active.
 * Main purpose - read messages sent from client, delegate these to the Backend object
 */
public class Session implements Runnable {

    private final Socket socket;
    private final ChatBackend chatBackend;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private boolean cancelled;

    private final String signatureAlgorithm;
    private final int keySize;


    public Session(Socket socket, ChatBackend chatBackend, String signatureAlgorithm, int keySize) {
        this.socket = socket;
        this.chatBackend = chatBackend;
        this.cancelled = false;
        this.signatureAlgorithm = signatureAlgorithm;
        this.keySize = keySize;

        try {
            socket.setSoTimeout(3000);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
            this.ois = ois;
            this.oos = oos;

            initiateKeys();

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

    private void initiateKeys() {

    }


    /**
     * Generates a public/private key pair using the DSA algorithm with 1024 byte keys
     * @return the KeyPair
     */
    private KeyPair getKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(signatureAlgorithm);
            keyGen.initialize(keySize);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    private void readFromClient() {
        while (!cancelled) {
            try {
                Command command = (Command) ois.readObject();

                if (command.equals(Command.DECLINED)) {
                    // remote host has closed
                    break;
                } else if (command.equals(Command.MESSAGE)) {
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

