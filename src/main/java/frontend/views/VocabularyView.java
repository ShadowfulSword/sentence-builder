// Oudom Pach OTP220001 10/20/2026 Initial Creation: Vocabulary and database explorer tab
// Ayush Kafley axk220167 11/05/2026 Revised: Connected VocabularyView to DatabaseManager via DbRunner
package frontend.views;

import backend.Database.DatabaseConfig;
import backend.Database.DatabaseManager;
import backend.Database.FollowerRow;
import frontend.DbRunner;
import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.TransitionItem;
import frontend.state.AppState.WordItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


/*
    Tab 2:
        provides searchable,sortable detail explorer that we can use to inspect word statistics and edit or delete database entries.
        Note: deleting will have to be changed as per ayush. will need to add some flag for less recommended rather than actually delete it
 */
public class VocabularyView {
    // Holds the shared application state containing words, transitions, and status text
    private final AppState state;

    //runner object to call all DB transactions on a background thread to not freeze the app
    private final DbRunner dbRunner;

    // Constructor receives the shared AppState so this tab stays synced with the rest of the UI
    public VocabularyView(AppState state) {
        this.state = state;
        this.dbRunner = new DbRunner(state);
    }

    //words shown in the table from the SQL table and only held in this tab
    private  final ObservableList<WordItem> words = FXCollections.observableArrayList();

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
        FilteredList<WordItem> filtered = new FilteredList<>(words);

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
            FXCollections.sort(words, c);
        });

        // Oudom Pach OTP220001 11/08/26 Revised: Reconciled vocabulary loader to fetch from MySQL via DbRunner when connected or fallback to in-memory state
        Runnable loadWords = () -> {
            if (state.isDatabaseConnected()) {
                state.setStatus("Loading vocab from MySQL Database...");
                dbRunner.rundb(DatabaseManager::getAllWords, rows -> {
                    words.setAll(rows.stream().map(r -> new WordItem(r.word(), r.total(), r.start(), r.end(), r.follows())).toList());
                    state.setStatus("Loaded " + rows.size() + " words from the Database");
                }, "Could not connect to Database: ");
            } else {
                words.setAll(state.getWords());
            }
        };

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
        // Oudom Pach OTP220001 11/08/26 Revised: Integrated DbRunner follower query with fallback to in-memory transitions map
        table.getSelectionModel().selectedItemProperty().addListener((o, a, w) -> {
            if (w == null) return;
            // Populate the text fields with the selected word's current stats
            selected.setText("Editing: " + w.text());
            total.setText("" + w.total());
            starts.setText("" + w.starts());
            ends.setText("" + w.ends());

            String word = w.text();
            pairs.getItems().clear();
            if (state.isDatabaseConnected()) {
                dbRunner.rundb(db -> db.getFollowers(word), followers -> {
                    // the user may have clicked another word while this query ran, drop stale answers
                    WordItem curr = table.getSelectionModel().getSelectedItem();
                    if (curr != null && curr.text().equals(word))
                        showFollowers(pairs, followers);
                }, "Could not load follow words: ");
            } else {
                Map<String, Integer> nextWords = state.getTransitions().get(word);
                if (nextWords != null) {
                    int sum = nextWords.values().stream().mapToInt(Integer::intValue).sum();
                    nextWords.entrySet().stream()
                            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                            .forEach(entry -> pairs.getItems().add(new TransitionItem(
                                    entry.getKey(), entry.getValue(),
                                    String.format("%.1f%%", 100.0 * entry.getValue() / Math.max(1, sum)))));
                }
            }
        });

        // Oudom Pach OTP220001 11/08/26 Revised: Combined DbRunner update persistence with bidirectional AppState synchronization
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

                if (state.isDatabaseConnected()) {
                    saveCount(w, t, s, e);
                } else {
                    WordItem updated = new WordItem(w.text(), t, s, e, w.follows());
                    int index = words.indexOf(w);
                    if (index >= 0) words.set(index, updated);
                    int stateIndex = state.getWords().indexOf(w);
                    if (stateIndex >= 0) state.getWords().set(stateIndex, updated);
                    state.setStatus("Word counts saved in memory; no database connected");
                }
            } catch (IllegalArgumentException ex) {
                state.setStatus("Enter nonnegative counts; start and end counts cannot exceed total.");
            }
        });

        // Oudom Pach OTP220001 11/08/26 Revised: Combined DbRunner word removal with local model purge and in-memory transition cleanup
        // Button to delete the selected word after asking for confirmation
        Button delete = UIFactory.button("Delete Word from DB", "danger", () -> {
            WordItem w = table.getSelectionModel().getSelectedItem();
            if (w == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete \"" + w.text() + "\" from " + (state.isDatabaseConnected() ? "the Database?" : "the local model?"),
                    ButtonType.CANCEL, ButtonType.OK);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                if (state.isDatabaseConnected()) {
                    dbRunner.rundb(db -> db.removeWord(w.text()), removed -> {
                        selected.setText("Select a word");
                        pairs.getItems().clear();
                        state.setStatus(removed ? "Deleted \"" + w.text() + "\" from the Database" : "\"" + w.text() + "\" was already gone");
                        loadWords.run();
                    }, "Couldn't delete from Database: ");
                } else {
                    selected.setText("Select a word");
                    pairs.getItems().clear();
                    words.remove(w);
                    state.getWords().remove(w);
                    state.getTransitions().remove(w.text());
                    state.getTransitions().values().forEach(m -> m.remove(w.text()));
                    state.setStatus("Word deleted from in-memory model");
                }
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

        loadWords.run();

        // Wrap the split pane in the standard page header layout and return it
        return UIFactory.page(
                "Every word has a story.",
                "Explore your corpus and inspect the connections between words.",
                split);
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // fill in the follow table and work out each word's prob based on frequency
    private void showFollowers(TableView<TransitionItem> pairs, List<FollowerRow> followers) {
        int sum = followers.stream().mapToInt(FollowerRow::follows).sum();
        for (FollowerRow f : followers)
            pairs.getItems().add(new TransitionItem(f.word(), f.follows(), String.format("%.1f%%", 100.0 * f.follows() / Math.max(1, sum))));
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Write the new update counts to MySQL then update the table once MySQL confirms change
    private void saveCount(WordItem word, int total, int start, int end) {
        dbRunner.rundb(db -> db.updateWordFrequencies(word.text(), total, start, end), saved -> {
            if (!saved) {
                state.setStatus("\"" + word.text() + "\" is no longer in the database. Please reload and try again");
                return;
            }
            WordItem updated = new WordItem(word.text(), total, start, end, word.follows());
            int index = words.indexOf(word);
            if (index >= 0)
                words.set(index, updated);
            int stateIndex = state.getWords().indexOf(word);
            if (stateIndex >= 0)
                state.getWords().set(stateIndex, updated);
            state.setStatus("Saved changes of \"" + word.text() + "\" to the Database");
        }, "Couldn't save to the Database: ");
    }
}
