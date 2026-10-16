package frontend;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;


// Oudom Pach OTP220001 10/16/2026, This is the main class for the JavaFX application. It sets up the primary stage and displays a simple label indicating that the GUI is ready.
public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        Label initLabel = new Label("Team 26 Sentence Builder - GUI Ready");
        StackPane root = new StackPane();
        root.getChildren().add(initLabel);
        //for now using a stack pane will be iterated over, but this sets up and renders the stage
        Scene scene = new Scene(root, 800, 600);
        primaryStage.setTitle("Sentence Builder");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}