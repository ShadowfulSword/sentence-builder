// Oudom Pach OTP220001 10/20/2026
package frontend;

import backend.Ingestion.CorpusTokenizer;
import backend.Ingestion.DocumentMetadata;
import backend.Ingestion.GutenbergIngestor;
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
        // Replace the load.setOnAction block in frontend/MainApp.java
        load.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Project Gutenberg Text", "*.txt"));
            var selected = chooser.showOpenDialog(stage);
            if (selected == null) return;

            load.setDisable(true);
            progress.setProgress(-1); // Progress bar untouched for your teammate
            importing.setText("Ingesting: " + selected.getName());

            CompletableFuture.supplyAsync(() -> {
                try {
                    GutenbergIngestor ingestor = new GutenbergIngestor();
                    if (!ingestor.openFile(selected.getAbsolutePath())) {
                        throw new RuntimeException("File not found");
                    }
                    if (!ingestor.extractMetadata()) {
                        throw new RuntimeException("Metadata extraction failed");
                    }
                    return ingestor;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).whenComplete((ingestor, error) -> Platform.runLater(() -> {
                load.setDisable(false);
                progress.setProgress(error == null ? 1 : 0); // Progress bar untouched

                if (error != null) {
                    importing.setText("Import failed");
                    state.setStatus("Unable to read file: " + error.getCause().getMessage());
                    return;
                }

                DocumentMetadata meta = ingestor.getMetadata();
                CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
                state.ingestCorpusStream(tokenizer);

                String bookTitle = meta.getTitle() != null ? meta.getTitle() : selected.getName();
                importing.setText("Imported: " + bookTitle + " — 100%");
                parsed.setText(String.format("Author: %s (Book #%d)", meta.getAuthor() != null ? meta.getAuthor() : "Unknown", meta.getBookNumber()));

                state.getCorpora().add(new CorpusItem(bookTitle, String.format("%,d bytes", meta.getSizeBytes()), UIFactory.now()));
                state.setStatus("Project Gutenberg corpus successfully parsed and loaded");
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