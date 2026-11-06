// Oudom Pach OTP220001 10/20/2026
// Ayush Kafly axk220167 11/05/2026
package frontend.views;

import frontend.DbRunner;
import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.CorpusItem;
import frontend.state.AppState.LogItem;
import frontend.state.AppState.WordItem;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Map;

/*
    Tab 3:
        Displays high-level corpus health metrics (KPI cards), tracks generated sentence history
        with automatic duplicate detection, and logs all imported Project Gutenberg text files.
 */
public class ReportsView {
    // Holds the shared application state containing words, transitions, history, and imported files
    private final AppState state;

    //runner object to call all DB transactions on a background thread to not freeze the app
    private final DbRunner dbRunner;
    //imported files at thte bottom table
    private final ObservableList<CorpusItem> files = FXCollections.observableArrayList();
    //formatter for imported time stamp
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MM d, yyyy hh:mm a");

    // Constructor receives the shared AppState so this tab stays synced with the rest of the UI
    public ReportsView(AppState state) {
        this.state = state;
        this.dbRunner = new DbRunner(state);
    }

    // Builds and returns the complete JavaFX layout for the Reports & Duplicate Audit tab
    public Node build() {
        // Large metric value labels for the 4 KPI summary cards at the top of the page
        Label vocabulary = UIFactory.label("", "metric");
        Label bigrams = UIFactory.label("", "metric");
        Label frequent = UIFactory.label("", "metric");
        Label duplicateRate = UIFactory.label("", "metric");

        // Calculate and update the duplicate rate
        Runnable updateMetrics = () -> {
           long unique = state.getHistory().stream().map(LogItem::sentence).distinct().count();
           int hist = state.getHistory().size();
           duplicateRate.setText(String.format("%,1f%%", hist == 0 ? 0 : 100.00 * (hist - unique) / hist));
        };
        updateMetrics.run(); // Calculate initial KPI values on startup

        // Re calculate the dupe rate when generated sentances get added
        state.getHistory().addListener((ListChangeListener<LogItem>) c -> updateMetrics.run());

        // Create a horizontal row holding the 4 KPI summary panels side-by-side
        HBox kpis = new HBox(15);
        String[] titles = {"Total Unique Vocabulary", "Total Bi-Gram Pairs", "Most Frequent Starting Word", "Overall Duplicate Rate"};
        Label[] values = {vocabulary, bigrams, frequent, duplicateRate};
        for (int i = 0; i < 4; i++) {
            VBox p = UIFactory.panel(titles[i], values[i]);
            HBox.setHgrow(p, Priority.ALWAYS); // Let each card expand equally across the row
            p.setMaxWidth(Double.MAX_VALUE);
            kpis.getChildren().add(p);
        }

        // Search bar to filter the generated sentence history by keyword
        TextField search = new TextField();
        search.setPromptText("Search generated sentences...");
        HBox.setHgrow(search, Priority.ALWAYS);

        // Checkbox to toggle showing only sentences that were generated more than once
        CheckBox onlyDuplicates = new CheckBox("Filter: Show Duplicates Only");
        // Wrap the history list in a FilteredList so we can search/filter without deleting log entries
        FilteredList<LogItem> filtered = new FilteredList<>(state.getHistory());
        // Define the filter condition: sentence must match search text AND (if checked) appear > 1 time
        Runnable filter = () -> filtered.setPredicate(l ->
                l.sentence().toLowerCase().contains(search.getText().toLowerCase()) &&
                        (!onlyDuplicates.isSelected() || state.countOccurrences(l.sentence()) > 1));
        // Re-run the filter whenever the user types in the search bar or toggles the checkbox
        search.textProperty().addListener((o, a, b) -> filter.run());
        onlyDuplicates.setOnAction(e -> filter.run());

        // Table displaying the history of generated sentences, timestamps, algorithms, and duplicate status
        TableView<LogItem> log = new TableView<>(filtered);
        UIFactory.column(log, "Generated sentence", LogItem::sentence);
        UIFactory.column(log, "Timestamp", LogItem::time);
        UIFactory.column(log, "Algorithm", LogItem::algorithm);
        // Status column dynamically checks how many times each sentence appears in the history log
        TableColumn<LogItem, String> statusColumn = UIFactory.column(log, "Status",
                l -> state.countOccurrences(l.sentence()) > 1
                        ? "Duplicate (Generated " + state.countOccurrences(l.sentence()) + "x)"
                        : "Unique");

        // Custom cell renderer that colors "Unique" green and "Duplicate" amber using CSS classes
        statusColumn.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("unique", "duplicate");
                if (!empty && value != null) {
                    getStyleClass().add(value.startsWith("Unique") ? "unique" : "duplicate");
                }
            }
        });

        // Make columns fit the table width and give the sentence column extra space
        log.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        log.getColumns().get(0).setPrefWidth(560);
        // Refresh the table and re-apply filters whenever a new sentence is generated in Tab 1
        state.getHistory().addListener((ListChangeListener<LogItem>) c -> {
            filter.run();
            log.refresh();
        });
        log.setPrefHeight(230);

        // Wrap the search controls and sentence history table in a styled panel
        VBox logs = UIFactory.panel("Generated sentence history", new HBox(15, search, onlyDuplicates), log);
        VBox.setVgrow(log, Priority.ALWAYS);

        // Bottom table displaying all imported .txt corpus files, word counts, and timestamps
        TableView<CorpusItem> files = new TableView<>(state.getCorpora());
        UIFactory.column(files, "Imported Project Gutenberg / .txt file", CorpusItem::file);
        UIFactory.column(files, "Words contributed", CorpusItem::count);
        UIFactory.column(files, "Import timestamp", CorpusItem::time);
        files.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        files.setPrefHeight(140);



        // Stack the KPI cards, sentence history panel, and imported files panel vertically
        VBox content = new VBox(18, kpis, logs, UIFactory.panel("Imported corpus files", files));
        VBox.setVgrow(logs, Priority.ALWAYS);

        // Wrap the content in the standard page header layout and return it
        return UIFactory.page(
                "The bigger picture.",
                "Corpus health, generation history, and patterns in your words.",
                content);
    }
}