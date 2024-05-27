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
    private boolean cancelled = false;
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
    }

    @Override
    public void run() {
        boolean declineAlreadySent = false;

        try {
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

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

            // allow for longer blocks during key exchange, since reads here are not looped;
            // a timeout here terminates the connection
            socket.setSoTimeout(10000);

            cryptographer = new Cryptographer();
            cryptographer.exchangeKeys(ois, oos);

            model.sessionStarted(this, cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());

            // reset to shorter timeout to allow for a faster response to a user cancel
            socket.setSoTimeout(1000);

            readFromRemoteHost();

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

    /**
     * Reads and decrypts messages from remote host until cancelled (the user terminates the session),
     * a "declined" command is received (remote host terminates), or an irrecoverable error occurs.
     * @throws Exception
     */
    private void readFromRemoteHost() throws Exception {

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
                    model.readMessage(message);

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

    /**
     * Writes an encrypted message to the remote host.
     * @param message the message to write
     */
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
                    model.wroteMessage(message);

                } catch (Exception e) {
                    model.errorWritingMessage(message);
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

