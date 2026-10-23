// Oudom Pach OTP220001 10/20/2026
package frontend;

import frontend.state.AppState;
import frontend.state.AppState.CorpusItem;
import frontend.views.EditorView;
import frontend.views.ReportsView;
import frontend.views.VocabularyView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/*
    MainApp Class:
    Serves as the root JavaFX application container. It instantiates the shared AppState,
    constructs the primary BorderPane layout shell, mounts the top header (with asynchronous file ingestion),
    embeds the three core views inside a TabPane, and binds the bottom status bar.
 */
public class MainApp extends Application {
    // Instantiate the central shared state container for the entire application UI
    private final AppState state = new AppState();

    /*
        start Method:
        Configures and displays the primary application stage, sets up the BorderPane root container,
        initializes the TabPane with its three modular views, binds the footer status bar, and loads theme.css.
     */
    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setTop(buildHeader(stage));

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Editor & Sentence Generator", new EditorView(state).build()),
                new Tab("Vocabulary & DB Explorer", new VocabularyView(state).build()),
                new Tab("Reports & Duplicate Audit", new ReportsView(state).build())
        );
        root.setCenter(tabs);

        Label status = new Label();
        status.textProperty().bind(state.statusTextProperty());
        status.getStyleClass().add("status-bar");
        status.setMaxWidth(Double.MAX_VALUE);
        root.setBottom(status);

        Scene scene = new Scene(root, 1280, 800);
        var cssUrl = getClass().getResource("/theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(Objects.requireNonNull(cssUrl).toExternalForm());
        }

        stage.setTitle("Sentence Builder");
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    /*
        buildHeader Method:
        Constructs the top navigation and status header. It includes branding, connection indicators,
        the live file ingestion progress bar and status tracker, and an asynchronous file chooser button
        to load external Project Gutenberg .txt corpora without blocking the UI thread.
     */
    private Node buildHeader(Stage stage) {
        Label title = UIFactory.label("Sentence Builder", "brand");
        VBox brand = new VBox(5, title, UIFactory.label("A little probability. Endless possibilities.", "muted"));
        Label connection = UIFactory.label("Local demo • MySQL not connected", "badge");

        ProgressBar progress = new ProgressBar(0.78);
        progress.setMaxWidth(Double.MAX_VALUE);
        Label importing = UIFactory.label("Demo ingestion: pride_and_prejudice.txt — 78%", "muted");
        Label parsed = UIFactory.label("96,420 words parsed · sample progress", "muted");
        VBox ingestion = new VBox(5, importing, progress, parsed);
        ingestion.setPrefWidth(330);

        Button load = new Button("+ Import .txt Corpus");
        load.getStyleClass().add("primary");
        load.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text corpus", "*.txt"));
            var selected = chooser.showOpenDialog(stage);
            if (selected == null) return;

            load.setDisable(true);
            progress.setProgress(-1);
            importing.setText("Ingesting: " + selected.getName());

            CompletableFuture.supplyAsync(() -> {
                try {
                    return Files.readString(selected.toPath());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((text, error) -> Platform.runLater(() -> {
                load.setDisable(false);
                progress.setProgress(error == null ? 1 : 0);
                if (error != null) {
                    importing.setText("Import failed");
                    state.setStatus("Unable to read file: " + error.getCause().getMessage());
                    return;
                }
                state.ingestDemoText(text);
                int count = text.isBlank() ? 0 : text.trim().split("\\s+").length;
                importing.setText("Imported: " + selected.getName() + " — 100%");
                parsed.setText(String.format("%,d words parsed", count));
                state.getCorpora().add(new CorpusItem(selected.getName(), String.format("%,d", count), UIFactory.now()));
                state.setStatus("Corpus imported into local in-memory model");
            }));
        });

        HBox row = new HBox(22, brand, connection, UIFactory.spacer(), ingestion, load);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("header");
        return row;
    }

    // Standard Java main entry point that launches the JavaFX application runtime
    public static void main(String[] args) {
        launch(args);
    }
}