import javafx.concurrent.Task;

import java.io.ObjectOutputStream;
import java.net.Socket;

public class SendAcceptedTask extends Task<Void> {

    private final Socket socket;

    public SendAcceptedTask(Socket socket) {
        this.socket = socket;
    }

    @Override
    protected Void call() throws Exception {
        try (socket) {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(Message.ACCEPTED);
            oos.flush();
        }
        return null;
    }
}
