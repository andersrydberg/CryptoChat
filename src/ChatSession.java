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
    private final Command response;
    private boolean cancelled;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private Cryptographer cryptographer;
    private final Object lock = new Object();


    /**
     * Called when localhost initiates an outgoing connection (i.e. acts as client)
     * @param chatBackend
     */
    public ChatSession(Socket socket, ChatBackend chatBackend) {
        this.socket = socket;
        this.chatBackend = chatBackend;
        this.response = null;
        this.cancelled = false;
    }

    /**
     * Called when localhost receives an incoming connection (i.e. acts as server)
     * @param socket
     * @param chatBackend
     * @param response
     */
    public ChatSession(Socket socket, ChatBackend chatBackend, Command response) {
        if (!response.equals(Command.ACCEPTED) && !response.equals(Command.DECLINED)) {
            throw new RuntimeException("ChatSession constructed with bad arguments");
        }

        this.socket = socket;
        this.chatBackend = chatBackend;
        this.response = response;
        this.cancelled = false;
    }

    @Override
    public void run() {
        try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            this.oos = oos;
            this.ois = ois;

            socket.setSoTimeout(1000);

            if (response == null) {
                while (!cancelled) {
                    try {
                        Command responseFromRemoteHost = (Command) ois.readObject();
                        if (responseFromRemoteHost.equals(Command.DECLINED)) {
                            chatBackend.outgoingConnectionRefused();
                            return;
                        }
                        break;
                    } catch (SocketTimeoutException e) {
                        // continue
                    }
                }
                if (cancelled) return;
            } else {
                oos.writeObject(response);
                oos.flush();

                if (response.equals(Command.DECLINED)) {
                    return;
                }
            }

            cryptographer = new Cryptographer();
            cryptographer.exchangeKeys(ois, oos);

            chatBackend.sessionStarted(this, cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());
            //chatBackend.receiveKeys(cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());


            readFromClient();

        } catch (Exception e) {
            // ignore
        } finally {
            chatBackend.stopActiveSession(); // check this
            chatBackend.sessionEnding();
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
        synchronized (lock) {
            try {
                oos.writeObject(Command.DECLINED);
            } catch (IOException e) {
                // ignore
            }
        }
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
                synchronized (lock) {
                    oos.writeObject(Command.MESSAGE);
                    oos.writeObject(cryptographer.encrypt(message));
                    oos.flush();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}

