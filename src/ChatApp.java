import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class ChatApp extends Application {

    private ChatController controller;

    @Override
    public void start(Stage primaryStage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("chat_view.fxml"));
        Parent root = fxmlLoader.load();
        controller = fxmlLoader.getController();

        primaryStage.setScene(new Scene(root));
        primaryStage.setTitle("Image Sender");
        primaryStage.show();

    }

}
