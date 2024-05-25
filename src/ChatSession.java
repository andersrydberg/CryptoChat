import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.SignedObject;

/**
 * Thread runs for as long as the chat session is active.
 * Main purpose - read messages sent from client, delegate these to the Backend object
 */
public class ChatSession implements Runnable {

    private final Socket socket;
    private final Model model;
    private final Command response;
    private boolean cancelled;
    private ObjectInputStream ois;
    private ObjectOutputStream oos;
    private Cryptographer cryptographer;
    private final Object lock = new Object();


    /**
     * Called when localhost initiates an outgoing connection (i.e. acts as client)
     * @param model
     */
    public ChatSession(Socket socket, Model model) {
        this.socket = socket;
        this.model = model;
        this.response = null;
        this.cancelled = false;
    }

    /**
     * Called when localhost receives an incoming connection (i.e. acts as server)
     * @param socket
     * @param model
     * @param response
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
                            model.sessionDeclinedByRemoteHost(getRemoteAddress());
                            return;
                        }
                        break;
                    } catch (SocketTimeoutException e) {
                        // continue
                    }
                }

                if (cancelled) {
                    model.sessionCancelled(getRemoteAddress());
                    return;
                }

            // remote host is the initiator, we send our response
            } else {
                oos.writeObject(response);
                oos.flush();

                if (response.equals(Command.DECLINED)) {
                    model.sessionDeclinedByUser(getRemoteAddress());
                    return;
                }
            }

            cryptographer = new Cryptographer();
            cryptographer.exchangeKeys(ois, oos);

            model.sessionStarted(this, cryptographer.getOwnPublicKey(), cryptographer.getOthersPublicKey());

            readFromClient();

            if (cancelled) {
                model.sessionCancelled(getRemoteAddress());
            }

        } catch (Exception e) {
            model.sessionEnding(getRemoteAddress());
        } finally {
            synchronized (lock) {
                try {
                    oos.writeObject(Command.DECLINED);
                    oos.flush();
                } catch (IOException e) {
                    // ignore
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
                    model.sessionEndedByRemoteHost(getRemoteAddress());
                    break;

                // incoming message
                } else if (command.equals(Command.MESSAGE)) {
                    // read encrypted message
                    String message = cryptographer.decipher((SignedObject) ois.readObject());
                    model.receiveMessage(message);

                // protocol breach (unexpected enum value)
                } else {
                    model.sessionEndedByProtocolBreach(getRemoteAddress());
                    break;
                }

            } catch (SocketTimeoutException e) {
                // ignore

            // protocol breach (unexpected object)
            } catch (ClassCastException e) {
                model.sessionEndedByProtocolBreach(getRemoteAddress());
                break;

            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace();
                model.sessionEnding(getRemoteAddress());
                break;
            }
        }
    }

    public void writeToRemoteHost(String message) {
        var writeTask = new Runnable() {
            @Override
            public void run() {
                try {
                    synchronized (lock) {
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

