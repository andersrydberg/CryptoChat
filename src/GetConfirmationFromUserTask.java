import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public class GetConfirmationFromUserTask extends Task<Boolean> {

    private final String inetAddress;

    public GetConfirmationFromUserTask(String inetAddress) {
        this.inetAddress = inetAddress;
    }

    @Override
    protected Boolean call() throws Exception {
        var result = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Accept connection from %s?", inetAddress))
                .showAndWait();
        return (result.isPresent() && result.get() == ButtonType.OK);
    }
}
