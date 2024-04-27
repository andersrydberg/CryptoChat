import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class PromptUserForAcceptTask extends Task<Void> {

    private final Socket socket;

    public PromptUserForAcceptTask(Socket socket) {
        this.socket = socket;
    }

    @Override
    protected Void call() throws Exception {
        // prompt user for confirmation
        final String inetAddress = socket.getInetAddress().toString();
        var confirm = new Task<Boolean>() {
            @Override
            protected Boolean call() throws Exception {
                var result = new Alert(Alert.AlertType.CONFIRMATION,
                        String.format("Accept connection from %s?", inetAddress))
                        .showAndWait();
                return (result.isPresent() && result.get() == ButtonType.OK);
            }
        };
        Platform.runLater(confirm);

        if (confirm.get()) {

        }

        try {
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(Message.DECLINED);
            oos.flush();
        } catch (IOException e) {
            // ignore
        }
        return null;
    }
}
