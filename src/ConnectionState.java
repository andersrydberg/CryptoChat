public enum ConnectionState {
    INACTIVE, // no ongoing chat session
    CONNECTING, // attempting to start a chat session with a remote host
    CANCELLING_OUTGOING_CONNECTION, // aborting connection attempt
    ACTIVE_SESSION, // connection with remote host established, chat session ongoing
    ENDING_SESSION // closing ongoing chat session
}