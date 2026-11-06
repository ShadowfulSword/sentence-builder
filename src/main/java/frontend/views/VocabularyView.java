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
/*
    Tab 2:
        provides searchable,sortable detail explorer that we can use to inspect word statistics and edit or delete database entries.
        Note: deleting will have to be changed as per ayush. will need to add some flag for less recommended rather than actually delete it
 */
public class VocabularyView {
    // Holds the shared application state containing words, transitions, and status text
    private final AppState state;

    // Constructor receives the shared AppState so this tab stays synced with the rest of the UI
    public VocabularyView(AppState state) {
        this.state = state;
    }

    // Builds and returns the complete JavaFX layout for the Vocabulary & DB Explorer tab
    public Node build() {

        // Search box to filter words by their starting letters
        TextField search = new TextField();
        search.setPromptText("Filter by word prefix...");
        HBox.setHgrow(search, Priority.ALWAYS); // Let the search bar stretch horizontally

        // Number spinner to filter out words below a certain total count
        Spinner<Integer> minimum = new Spinner<>(0, 1000000, 1);
        minimum.setPrefWidth(100);

        // Wrap the main word list in a FilteredList so we can hide/show rows without deleting data
        FilteredList<WordItem> filtered = new FilteredList<>(state.getWords());

        // Define the filter condition: word must match search prefix AND meet minimum frequency
        Runnable filter = () -> filtered.setPredicate(
                w -> w.text().startsWith(search.getText().toLowerCase()) && w.total() >= minimum.getValue());

        // Re-run the filter automatically whenever the user types in search or changes the spinner
        search.textProperty().addListener((o, a, b) -> filter.run());
        minimum.valueProperty().addListener((o, a, b) -> filter.run());
        filter.run(); // Apply initial filter on startup

        // Create the main table displaying the filtered vocabulary words and their statistics
        TableView<WordItem> table = new TableView<>(filtered);
        UIFactory.column(table, "Word", WordItem::text);
        UIFactory.column(table, "Total Frequency", w -> "" + w.total());
        UIFactory.column(table, "Sentence Start Count", w -> "" + w.starts());
        UIFactory.column(table, "Sentence End Count", w -> "" + w.ends());
        UIFactory.column(table, "Unique Follow Words", w -> "" + w.follows());

        // Make all columns resize evenly to fit the table width
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Dropdown menu to let the user sort the vocabulary list by different metrics
        ComboBox<String> sort = new ComboBox<>(FXCollections.observableArrayList(
                "Alphabetical (A–Z)", "Total Frequency", "Start Count", "End Count"));
        sort.getSelectionModel().selectFirst();
        // When a sort option is picked, sort the underlying words list using the matching Comparator
        sort.setOnAction(e -> {
            Comparator<WordItem> c = switch (sort.getSelectionModel().getSelectedIndex()) {
                case 1 -> Comparator.comparingInt(WordItem::total).reversed();  // Highest total first
                case 2 -> Comparator.comparingInt(WordItem::starts).reversed(); // Highest start count first
                case 3 -> Comparator.comparingInt(WordItem::ends).reversed();   // Highest end count first
                default -> Comparator.comparing(WordItem::text);                // A-Z alphabetical
            };
            FXCollections.sort(state.getWords(), c);
        });

        // Assemble the left panel containing the filter bar, sort dropdown, and vocabulary table
        VBox left = UIFactory.panel("Vocabulary explorer",
                new HBox(10, search, new Label("Min Frequency ≥"), minimum),
                new HBox(10, new Label("Sort:"), sort),
                table);
        VBox.setVgrow(table, Priority.ALWAYS); // Let the table expand vertically to fill space

        // Right-side inspector controls for viewing and editing the currently selected word
        Label selected = UIFactory.label("Select a word", "title");
        TextField total = new TextField();
        TextField starts = new TextField();
        TextField ends = new TextField();

        // Secondary table showing which words follow the selected word and their probabilities
        TableView<TransitionItem> pairs = new TableView<>();
        UIFactory.column(pairs, "Following Word", TransitionItem::word);
        UIFactory.column(pairs, "Pair Count", t -> "" + t.count());
        UIFactory.column(pairs, "Transition Probability %", TransitionItem::probability);
        pairs.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        pairs.setPrefHeight(230);

        // Listener that triggers whenever the user clicks a word row in the left table
        table.getSelectionModel().selectedItemProperty().addListener((o, a, w) -> {
            if (w == null) return;
            // Populate the text fields with the selected word's current stats
            selected.setText(w.text());
            total.setText("" + w.total());
            starts.setText("" + w.starts());
            ends.setText("" + w.ends());
            // Look up all words that follow the selected word and calculate their % probability
            var map = state.getTransitions().getOrDefault(w.text(), Map.of());
            int sum = map.values().stream().mapToInt(Integer::intValue).sum();
            pairs.getItems().clear();
            map.forEach((next, n) -> pairs.getItems().add(
                    new TransitionItem(next, n, String.format("%.1f%%", 100.0 * n / Math.max(1, sum)))));
        });

        // Button to validate and save manual edits made to a word's counts
        Button save = UIFactory.button("Save Changes to Database", "primary", () -> {
            WordItem w = table.getSelectionModel().getSelectedItem();
            if (w == null) return;
            try {
                // Parse text fields into integers and verify counts are logical and non-negative
                int t = Integer.parseInt(total.getText());
                int s = Integer.parseInt(starts.getText());
                int e = Integer.parseInt(ends.getText());
                if (t < 0 || s < 0 || e < 0 || s > t || e > t) throw new IllegalArgumentException();
                // Replace the old word record in the state list with the updated counts
                state.getWords().set(state.getWords().indexOf(w), new WordItem(w.text(), t, s, e, w.follows()));
                state.setStatus("Word counts saved in memory; no database configured");
            } catch (IllegalArgumentException ex) {
                state.setStatus("Enter nonnegative counts; start and end counts cannot exceed total.");
            }
        });

        // Button to delete the selected word after asking for confirmation
        Button delete = UIFactory.button("Delete Word from DB", "danger", () -> {
            WordItem w = table.getSelectionModel().getSelectedItem();
            if (w == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete '" + w.text() + "' from the local model?", ButtonType.CANCEL, ButtonType.OK);
            // If user clicks OK, remove the word and all of its follow-word transitions
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                state.getWords().remove(w);
                state.getTransitions().remove(w.text());
                state.getTransitions().values().forEach(m -> m.remove(w.text()));
                selected.setText("Select a word");
                pairs.getItems().clear();
                state.setStatus("Word deleted from in-memory model");
            }
        });

        // Assemble the right panel containing the word details editor and follow-word table
        VBox right = UIFactory.panel("Word details · local editor",
                selected,
                new Label("Total Count"), total,
                new Label("Start Count"), starts,
                new Label("End Count"), ends,
                new Label("Follow-word transitions"), pairs,
                save, delete);

        // Place the left explorer (65% width) and right editor (35% width) side-by-side
        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.65);

        // Wrap the split pane in the standard page header layout and return it
        return UIFactory.page(
                "Every word has a story.",
                "Explore your corpus and inspect the connections between words.",
                split);
    }
}