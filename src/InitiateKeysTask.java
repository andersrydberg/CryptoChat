/**
 * Runs after the receiving host has accepted a connection.
 * Initiate public and private key pair, send public key,
 * reсeive remote host's public key, then delegate to OngoingSession
 */
public class InitiateKeysTask implements Runnable {

    private final Backend backend;

    public InitiateKeysTask(Backend backend) {
        this.backend = backend;
    }

    @Override
    public void run() {

    }

}
