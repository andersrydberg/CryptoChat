import javafx.concurrent.Task;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class SendDeclinedAndCloseTask extends Task<Void> {

    private final Socket socket;

    public SendDeclinedAndCloseTask(Socket socket) {
        this.socket = socket;
    }

    @Override
    protected Void call() throws Exception {
        try (socket) {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(Message.DECLINED);
            oos.flush();
        } catch (IOException e) {
           // ignore
        }
        return null;
    }
}
