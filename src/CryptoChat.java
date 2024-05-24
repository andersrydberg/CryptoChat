import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * JavaFX main class
 */
public class CryptoChat extends Application {

    private Controller controller;

    /**
     * Loads the fxml file that specifies the main scene layout.
     * User input triggers event handlers in the controller.
     */
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("chat_view.fxml"));
        Parent root = fxmlLoader.load();
        controller = fxmlLoader.getController();

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("CryptoChat");
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    /**
     * Prepare the application for shutdown. Called whenever the user
     * makes an exit request, e.g. presses the "X" button.
     */
    @Override
    public void stop() {
        controller.shutdown();
    }


}
