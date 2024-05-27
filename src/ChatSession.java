import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SignedObject;

/**
 * A Runnable charged with all communication with remote host once a socket has connected.
 * If the local user is the initiating party, reads the accept/decline response from remote
 * host and reacts accordingly (closes the socket and ends execution if response is a decline).
 * If the local user is the responding party, sends the response (and subsequently closes the
 * socket and ends execution if response is a decline). If the response is an accept, performs
 * public key exchange, after which encrypted and signed messages can be read and written
 * (message writes are performed on a separate thread). Runs until either party disconnects,
 * or any kind of unrecoverable error occurs.
 */
public class ChatSession implements Runnable {

    private final Socket socket;
    private final Model model;
    private final Command response;
    private boolean cancelled;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private Cryptographer cryptographer;
    // writes are done from different threads, so we synchronize writes to avoid incorrect interleaving
    private final Object writeLock = new Object();


    /**
     * Used when localhost initiates an outgoing connection (i.e. acts as client)
     * @param socket the connected socket on which communication is to be performed
     * @param model the calling object
     */
    public ChatSession(Socket socket, Model model) {
        this.socket = socket;
        this.model = model;
        this.response = null;
        this.cancelled = false;
    }

    /**
     * Used when localhost receives an incoming connection (i.e. acts as server)
     * @param socket the connected socket on which communication is to be performed
     * @param model the calling object
     * @param response the response to be sent to remote host
     */
    public ChatSession(Socket socket, Model model, Command response) {
        if (!response.equals(Command.ACCEPTED) && !response.equals(Command.DECLINED)) {
            throw new RuntimeException("ChatSession constructed with bad arguments");
        }

        this.socket = socket;
        this.model = model;
        this.response = response;
        this.cancelled = false;
    }

    @Override
    public void run() {
        boolean declineAlreadySent = false;

        try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
            this.oos = oos;
            this.ois = ois;

            // set a timeout so reads do not block indefinitely, so we can check if the thread has been cancelled
            socket.setSoTimeout(1000);

            // local user is the initiator, we expect a response from remote host
            if (response == null) {

                while (!cancelled) {
                    try {
                        Command responseFromRemoteHost = (Command) ois.readObject();
                        if (responseFromRemoteHost.equals(Command.DECLINED)) {
                            model.sessionEnded("Remote host " + getRemoteAddress() + " has declined your invite.");
                            declineAlreadySent = true;
                            return;
                        }
                        // else Command.ACCEPTED
                        break;
                    } catch (SocketTimeoutException e) {
                        // continue
                    }
                }

                if (cancelled) {
                    model.sessionEnded("You have cancelled the outgoing connection to " + getRemoteAddress() + ".");
                    return;
                }

            // remote host is the initiator, we send our response
            } else {
                synchronized (writeLock) {
                    oos.writeObject(response);
                    oos.flush();
                }

                if (response.equals(Command.DECLINED)) {
                    model.sessionEnded("You have declined an invite from " + getRemoteAddress() + ".");
                    declineAlreadySent = true;
                    return;
                }
            }

            cryptographer = new Cryptographer();
            cryptographer.exchangeKeys(ois, oos);

            model.sessionStarted(this, cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());

            readFromClient();

            if (cancelled) {
                model.sessionEnded("You have ended the chat session with " + getRemoteAddress() + ".");
            }

        // protocol breach (unexpected object)
        } catch (ClassCastException e) {
            model.sessionEnded("There was an error communicating with " + getRemoteAddress() + ". Chat session ending.");

        } catch (Exception e) {
            e.printStackTrace();
            model.sessionEnded("Chat session with " + getRemoteAddress() + " ending.");

        } finally {
            if (!declineAlreadySent) {
                // notify remote host that session has ended
                synchronized (writeLock) {
                    try {
                        oos.writeObject(Command.DECLINED);
                        oos.flush();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }

            try {
                socket.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private void readFromClient() throws Exception {

        while (!cancelled) {
            try {
                Command command = (Command) ois.readObject();

                // remote host has quit
                if (command.equals(Command.DECLINED)) {
                    model.sessionEnded("Remote host at " + getRemoteAddress() + " has left the chat session.");
                    break;

                    // incoming message
                } else if (command.equals(Command.MESSAGE)) {
                    // read encrypted message
                    String message = cryptographer.decipher((SignedObject) ois.readObject());
                    model.receiveMessage(message);

                    // protocol breach (unexpected enum value)
                } else {
                    model.sessionEnded("There was an error communicating with " + getRemoteAddress() + ". Chat session ending.");
                    break;
                }

            } catch (SocketTimeoutException e) {
                // ignore

            // signature could not be verified
            } catch (FailedVerificationException e) {
                model.sessionEnded("The message could not be verified with remote host's public key. Chat session with " + getRemoteAddress() + " ending.");
                break;

            }
        }

    }

    public void writeToRemoteHost(String message) {
        var writeTask = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (writeLock) {
                        oos.writeObject(Command.MESSAGE);
                        oos.writeObject(cryptographer.cipher(message));
                        oos.flush();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Thread thread = new Thread(writeTask);
        thread.setDaemon(true);
        thread.start();
    }

    public void cancel() {
        cancelled = true;
    }

    public String getRemoteAddress() {
        return socket.getInetAddress().toString();
    }

}

