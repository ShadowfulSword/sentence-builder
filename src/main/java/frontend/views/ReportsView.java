// Oudom Pach OTP220001 10/20/2026
package frontend.views;

import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.CorpusItem;
import frontend.state.AppState.LogItem;
import frontend.state.AppState.WordItem;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Comparator;
import java.util.Map;

public class ReportsView {
    private final AppState state;

    public ReportsView(AppState state) {
        this.state = state;
    }

    public Node build() {
        Label vocabulary = UIFactory.label("", "metric");
        Label bigrams = UIFactory.label("", "metric");
        Label frequent = UIFactory.label("", "metric");
        Label duplicateRate = UIFactory.label("", "metric");

        Runnable updateMetrics = () -> {
            vocabulary.setText("" + state.getWords().size());
            bigrams.setText("" + state.getTransitions().values().stream().mapToInt(Map::size).sum());
            frequent.setText(state.getWords().stream()
                    .max(Comparator.comparingInt(WordItem::starts))
                    .map(WordItem::text).orElse("—"));
            long unique = state.getHistory().stream().map(LogItem::sentence).distinct().count();
            int totalHistory = state.getHistory().size();
            duplicateRate.setText(String.format("%.1f%%", totalHistory == 0 ? 0 : 100.0 * (totalHistory - unique) / totalHistory));
        };
        updateMetrics.run();

        state.getWords().addListener((ListChangeListener<WordItem>) c -> updateMetrics.run());
        state.getHistory().addListener((ListChangeListener<LogItem>) c -> updateMetrics.run());

        HBox kpis = new HBox(15);
        String[] titles = {"Total Unique Vocabulary", "Total Bi-Gram Pairs", "Most Frequent Starting Word", "Overall Duplicate Rate"};
        Label[] values = {vocabulary, bigrams, frequent, duplicateRate};
        for (int i = 0; i < 4; i++) {
            VBox p = UIFactory.panel(titles[i], values[i]);
            HBox.setHgrow(p, Priority.ALWAYS);
            p.setMaxWidth(Double.MAX_VALUE);
            kpis.getChildren().add(p);
        }

        TextField search = new TextField();
        search.setPromptText("Search generated sentences...");
        HBox.setHgrow(search, Priority.ALWAYS);

        CheckBox onlyDuplicates = new CheckBox("Filter: Show Duplicates Only");
        FilteredList<LogItem> filtered = new FilteredList<>(state.getHistory());
        Runnable filter = () -> filtered.setPredicate(l ->
                l.sentence().toLowerCase().contains(search.getText().toLowerCase()) &&
                        (!onlyDuplicates.isSelected() || state.countOccurrences(l.sentence()) > 1));
        search.textProperty().addListener((o, a, b) -> filter.run());
        onlyDuplicates.setOnAction(e -> filter.run());

        TableView<LogItem> log = new TableView<>(filtered);
        UIFactory.column(log, "Generated sentence", LogItem::sentence);
        UIFactory.column(log, "Timestamp", LogItem::time);
        UIFactory.column(log, "Algorithm", LogItem::algorithm);
        TableColumn<LogItem, String> statusColumn = UIFactory.column(log, "Status",
                l -> state.countOccurrences(l.sentence()) > 1
                        ? "Duplicate (Generated " + state.countOccurrences(l.sentence()) + "x)"
                        : "Unique");

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

        log.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        log.getColumns().get(0).setPrefWidth(560);
        state.getHistory().addListener((ListChangeListener<LogItem>) c -> {
            filter.run();
            log.refresh();
        });
        log.setPrefHeight(230);

        VBox logs = UIFactory.panel("Generated sentence history", new HBox(15, search, onlyDuplicates), log);
        VBox.setVgrow(log, Priority.ALWAYS);

        TableView<CorpusItem> files = new TableView<>(state.getCorpora());
        UIFactory.column(files, "Imported Project Gutenberg / .txt file", CorpusItem::file);
        UIFactory.column(files, "Words contributed", CorpusItem::count);
        UIFactory.column(files, "Import timestamp", CorpusItem::time);
        files.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        files.setPrefHeight(140);

        VBox content = new VBox(18, kpis, logs, UIFactory.panel("Imported corpus files", files));
        VBox.setVgrow(logs, Priority.ALWAYS);

        return UIFactory.page(
                "The bigger picture.",
                "Corpus health, generation history, and patterns in your words.",
                content);
    }
}