// Oudom Pach OTP220001 10/20/2026
package frontend.views;

import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.TransitionItem;
import frontend.state.AppState.WordItem;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Comparator;
import java.util.Map;

public class VocabularyView {
    private final AppState state;

    public VocabularyView(AppState state) {
        this.state = state;
    }

    public Node build() {
        TextField search = new TextField();
        search.setPromptText("Filter by word prefix...");
        HBox.setHgrow(search, Priority.ALWAYS);

        Spinner<Integer> minimum = new Spinner<>(0, 1000000, 1);
        minimum.setPrefWidth(100);

        FilteredList<WordItem> filtered = new FilteredList<>(state.getWords());
        Runnable filter = () -> filtered.setPredicate(
                w -> w.text().startsWith(search.getText().toLowerCase()) && w.total() >= minimum.getValue());
        search.textProperty().addListener((o, a, b) -> filter.run());
        minimum.valueProperty().addListener((o, a, b) -> filter.run());
        filter.run();

        TableView<WordItem> table = new TableView<>(filtered);
        UIFactory.column(table, "Word", WordItem::text);
        UIFactory.column(table, "Total Frequency", w -> "" + w.total());
        UIFactory.column(table, "Sentence Start Count", w -> "" + w.starts());
        UIFactory.column(table, "Sentence End Count", w -> "" + w.ends());
        UIFactory.column(table, "Unique Follow Words", w -> "" + w.follows());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList(
                "Alphabetical (A–Z)", "Total Frequency", "Start Count", "End Count"));
        sort.getSelectionModel().selectFirst();
        sort.setOnAction(e -> {
            Comparator<WordItem> c = switch (sort.getSelectionModel().getSelectedIndex()) {
                case 1 -> Comparator.comparingInt(WordItem::total).reversed();
                case 2 -> Comparator.comparingInt(WordItem::starts).reversed();
                case 3 -> Comparator.comparingInt(WordItem::ends).reversed();
                default -> Comparator.comparing(WordItem::text);
            };
            FXCollections.sort(state.getWords(), c);
        });

        VBox left = UIFactory.panel("Vocabulary explorer",
                new HBox(10, search, new Label("Min Frequency ≥"), minimum),
                new HBox(10, new Label("Sort:"), sort),
                table);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label selected = UIFactory.label("Select a word", "title");
        TextField total = new TextField();
        TextField starts = new TextField();
        TextField ends = new TextField();

        TableView<TransitionItem> pairs = new TableView<>();
        UIFactory.column(pairs, "Following Word", TransitionItem::word);
        UIFactory.column(pairs, "Pair Count", t -> "" + t.count());
        UIFactory.column(pairs, "Transition Probability %", TransitionItem::probability);
        pairs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        pairs.setPrefHeight(230);

        table.getSelectionModel().selectedItemProperty().addListener((o, a, w) -> {
            if (w == null) return;
            selected.setText(w.text());
            total.setText("" + w.total());
            starts.setText("" + w.starts());
            ends.setText("" + w.ends());
            var map = state.getTransitions().getOrDefault(w.text(), Map.of());
            int sum = map.values().stream().mapToInt(Integer::intValue).sum();
            pairs.getItems().clear();
            map.forEach((next, n) -> pairs.getItems().add(
                    new TransitionItem(next, n, String.format("%.1f%%", 100.0 * n / Math.max(1, sum)))));
        });

        Button save = UIFactory.button("Save Changes to Database", "primary", () -> {
            WordItem w = table.getSelectionModel().getSelectedItem();
            if (w == null) return;
            try {
                int t = Integer.parseInt(total.getText());
                int s = Integer.parseInt(starts.getText());
                int e = Integer.parseInt(ends.getText());
                if (t < 0 || s < 0 || e < 0 || s > t || e > t) throw new IllegalArgumentException();
                state.getWords().set(state.getWords().indexOf(w), new WordItem(w.text(), t, s, e, w.follows()));
                state.setStatus("Word counts saved in memory; no database configured");
            } catch (IllegalArgumentException ex) {
                state.setStatus("Enter nonnegative counts; start and end counts cannot exceed total.");
            }
        });

        Button delete = UIFactory.button("Delete Word from DB", "danger", () -> {
            WordItem w = table.getSelectionModel().getSelectedItem();
            if (w == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete '" + w.text() + "' from the local model?", ButtonType.CANCEL, ButtonType.OK);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                state.getWords().remove(w);
                state.getTransitions().remove(w.text());
                state.getTransitions().values().forEach(m -> m.remove(w.text()));
                selected.setText("Select a word");
                pairs.getItems().clear();
                state.setStatus("Word deleted from in-memory model");
            }
        });

        VBox right = UIFactory.panel("Word details · local editor",
                selected,
                new Label("Total Count"), total,
                new Label("Start Count"), starts,
                new Label("End Count"), ends,
                new Label("Follow-word transitions"), pairs,
                save, delete);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.65);

        return UIFactory.page(
                "Every word has a story.",
                "Explore your corpus and inspect the connections between words.",
                split);
    }
}