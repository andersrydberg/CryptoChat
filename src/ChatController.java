import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

public class ChatController {
    public Button myClickButton;
    public Button myClearButton;
    public TextArea myText;

    private int counter = 0;

    public void clickButtonHandler (ActionEvent event) {
        counter++;
        myText.appendText(String.format("Button clicked %s\n", counter));
    }

    public void clearButtonHandler(ActionEvent event) {
        counter = 0;
        myText.clear();
    }
}
