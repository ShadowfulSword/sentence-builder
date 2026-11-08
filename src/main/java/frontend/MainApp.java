// Oudom Pach OTP220001 10/16/2026 Initial Creation: Root application container and JavaFX stage
package frontend;

import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;
import backend.Ingestion.CorpusTokenizer;
import backend.Ingestion.DocumentMetadata;
import backend.Ingestion.GutenbergIngestor;
import backend.Ingestion.Token;
import frontend.state.AppState;
import frontend.state.AppState.CorpusItem;
import frontend.views.EditorView;
import frontend.views.ReportsView;
import frontend.views.VocabularyView;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;


import java.io.File;
import java.io.IOException;
import java.util.*;

/*
    MainApp Class:
    Serves as the root JavaFX application container. It instantiates the shared AppState,
    constructs the primary BorderPane layout shell, mounts the top header (with asynchronous file ingestion),
    embeds the three core views inside a TabPane, and binds the bottom status bar.
 */
public class MainApp extends Application {
    // Instantiate the central shared state container for the entire application UI
    private final AppState state = new AppState();
    private DatabaseManager databaseManager;
    private boolean isDatabaseConnected = false;

    private record IngestionResult(
            DocumentMetadata metadata,
            String title,
            int totalWords,
            Collection<WordData> words,
            Collection<TransitionData> transitions,
            boolean savedToDatabase
    ) {}

    /*
        start Method:
        Configures and displays the primary application stage, sets up the BorderPane root container,
        initializes the TabPane with its three modular views, binds the footer status bar, and loads theme.css.
     */
    @Override
    public void start(Stage stage) {
        // Oudom Pach OTP220001 11/3/2026 Revised: Connect to MySQL on startup and load persistent database state
        // Attempt database connection on startup
        try {
            databaseManager = DatabaseManager.createDefault();
            isDatabaseConnected = true;
            state.loadFromDatabase(databaseManager);
        } catch (Exception ex) {
            isDatabaseConnected = false;
            databaseManager = null;
            state.setStatus("MySQL offline — running in local demo mode (" + ex.getMessage() + ")");
        }

        // Clean up connection on exit
        stage.setOnCloseRequest(e -> {
            if (databaseManager != null) {
                try {
                    databaseManager.close();
                } catch (Exception ex) {
                    System.err.println("Error closing database: " + ex.getMessage());
                }
            }
        });

        // Oudom Pach OTP220001 10/20/2026 Revised: Implemented BorderPane layout with 3-tab navigation shell and theme styling
        BorderPane root = new BorderPane();
        root.setTop(buildHeader(stage));
        //each tab shares the same state and build method returnes node for javafx scene graph subtree, unavailble means tab can't be closed
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                new Tab("Editor & Sentence Generator", new EditorView(state).build()),
                new Tab("Vocabulary & DB Explorer", new VocabularyView(state).build()),
                new Tab("Reports & Duplicate Audit", new ReportsView(state).build())
        );
        root.setCenter(tabs);

        //status bar
        Label status = new Label();

        //observer property here for .bind when any code calls it label automatically updates
        status.textProperty().bind(state.statusTextProperty());
        status.getStyleClass().add("status-bar");
        status.setMaxWidth(Double.MAX_VALUE);
        root.setBottom(status);

        //sets the scene and uses the theme.css to import styling
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

    // Oudom Pach OTP220001 10/20/2026 Revised: Added navigation header with brand and ingestion progress UI
    //UI factory is called here and creates styled label in one call so we dont have to write any boilerplate
    private Node buildHeader(Stage stage) {
        Label title = UIFactory.label("Sentence Builder", "brand");
        VBox brand = new VBox(5, title, UIFactory.label("A little probability. Endless possibilities.", "muted"));
        Label connection = UIFactory.label(
                isDatabaseConnected ? "● MySQL Connected" : "○ MySQL Offline (Local Demo)",
                "badge"
        );

        ProgressBar progress = new ProgressBar(0.0);
        progress.setMaxWidth(Double.MAX_VALUE);
        Label importing = UIFactory.label("Ingestion: Idle", "muted");
        Label parsed = UIFactory.label("Select a Project Gutenberg .txt corpus to begin", "muted");
        VBox ingestion = new VBox(5, importing, progress, parsed);
        ingestion.setPrefWidth(330);

        // Oudom Pach OTP220001 10/25/2026 Revised: Hooked up GutenbergIngestor and background Task ingestion
        //this is where connects to backend
        Button load = new Button("+ Import .txt Corpus");
        load.getStyleClass().add("primary");
        load.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Project Gutenberg Corpus");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Project Gutenberg Text (*.txt)", "*.txt"));
            File selected = chooser.showOpenDialog(stage);
            if (selected == null) return;

            load.setDisable(true);
            progress.setProgress(0.0);
            importing.setText("Opening: " + selected.getName());
            parsed.setText("Starting background ingestion...");

            // Oudom Pach OTP220001 11/08/26 Revised: Preserved JavaFX Task async ingestion with live byte progress, duplicate validation, and MySQL batch persistence over timeline polling
            // Background worker task to avoid freezing the JavaFX UI
            Task<IngestionResult> task = new Task<>() {
                @Override
                protected IngestionResult call() throws Exception {
                    updateMessage("Opening " + selected.getName() + "...");
                    updateProgress(0, 100);

                    GutenbergIngestor ingestor = new GutenbergIngestor();
                    if (!ingestor.openFile(selected.getAbsolutePath())) {
                        throw new IOException("File not found or cannot be opened: " + selected.getName());
                    }

                    updateMessage("Extracting metadata...");
                    if (!ingestor.extractMetadata()) {
                        throw new IOException("Project Gutenberg metadata extraction failed: " + selected.getName());
                    }
                    // Oudom Pach OTP220001 11/3/2026 Revised: Added MySQL duplicate check and batch persistence for words and transitions
                    DocumentMetadata meta = ingestor.getMetadata();
                    String bookTitle = (meta.getTitle() != null && !meta.getTitle().isBlank())
                            ? meta.getTitle()
                            : selected.getName();

                    // Prevent duplicate imports into the database
                    if (isDatabaseConnected && databaseManager != null && databaseManager.hasImportedFile(bookTitle)) {
                        throw new IllegalStateException("Corpus \"" + bookTitle + "\" has already been imported into the database.");
                    }

                    updateMessage("Tokenizing & analyzing " + bookTitle + "...");
                    CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
                    Map<String, WordData> words = new HashMap<>();
                    Map<String, TransitionData> transitions = new HashMap<>();
                    Token prev = null;
                    int totalWords = 0;
                    long totalBytes = meta.getSizeBytes() > 0 ? meta.getSizeBytes() : selected.length();

                    while (tokenizer.hasNext()) {
                        Token token = tokenizer.next();
                        String word = token.getWord();
                        if (word == null || word.isEmpty()) continue;
                        totalWords++;

                        WordData exists = words.get(word);
                        if (exists == null) {
                            words.put(word, new WordData(word, token.isStart(), token.isEnd()));
                        } else {
                            exists.addOccurrence(token.isStart(), token.isEnd());
                        }

                        if (prev != null && !prev.isEnd()) {
                            String key = prev.getWord() + "\0" + word;
                            TransitionData t = transitions.get(key);
                            if (t == null) {
                                transitions.put(key, new TransitionData(prev.getWord(), word));
                            } else {
                                t.addFrequency();
                            }
                        }
                        prev = token;

                        if (totalWords % 1000 == 0) {
                            long bytesRead = ingestor.getBytesRead();
                            updateProgress(bytesRead, totalBytes);
                            updateMessage(String.format("Ingesting: %,d words (%,d KB)...", totalWords, bytesRead / 1024));
                        }
                    }

                    boolean savedToDb = false;
                    if (isDatabaseConnected && databaseManager != null) {
                        updateMessage(String.format("Persisting %,d words and %,d bi-grams to MySQL...", words.size(), transitions.size()));
                        databaseManager.batchAdd(words.values(), transitions.values());
                        databaseManager.addImportedFile(bookTitle, totalWords);
                        savedToDb = true;
                    }

                    return new IngestionResult(meta, bookTitle, totalWords, words.values(), transitions.values(), savedToDb);
                }
            };

            // Bind task progress & status text directly to UI controls
            progress.progressProperty().bind(task.progressProperty());
            importing.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(e -> {
                progress.progressProperty().unbind();
                importing.textProperty().unbind();
                load.setDisable(false);
                progress.setProgress(1.0);

                IngestionResult result = task.getValue();
                DocumentMetadata meta = result.metadata();
                importing.setText("Imported: " + result.title() + " — 100%");
                parsed.setText(String.format("Author: %s (Book #%d) · %,d words parsed",
                        meta.getAuthor() != null ? meta.getAuthor() : "Unknown",
                        meta.getBookNumber(),
                        result.totalWords()));

                state.applyIngestedCorpus(result.title(), result.totalWords(), meta.getSizeBytes(), result.words(), result.transitions());
                state.setStatus(result.savedToDatabase()
                        ? "Corpus saved to MySQL and loaded into memory: " + result.title()
                        : "Corpus loaded into memory (local offline mode): " + result.title());
            });

            task.setOnFailed(e -> {
                progress.progressProperty().unbind();
                importing.textProperty().unbind();
                load.setDisable(false);
                progress.setProgress(0.0);

                Throwable error = task.getException();
                String message = error != null ? error.getMessage() : "Unknown error during ingestion";
                importing.setText("Import failed");
                parsed.setText(message);
                state.setStatus("Unable to read file: " + message);

                Alert alert = new Alert(Alert.AlertType.WARNING, message, ButtonType.OK);
                alert.setTitle("Import Notice");
                alert.setHeaderText("Corpus Ingestion Notice");
                alert.showAndWait();
            });

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
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
