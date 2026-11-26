// Oudom Pach OTP220001 10/20/2026 Initial Creation: Composition text editor and sentence generator tab
// Ayush Kafley axk220167 11/05/2026 Revised: Integrated DbRunner for database-driven random word selection
// Andrew Park AXP220253 11/16/2026 Revised: Implemented spinner UI for temperature algo driven sentence gen
// Oudom Pach OTP220001 11/19/26 Revised: Bug fix to remove trailing space when inserting suggested word
package frontend.views;

import backend.Database.DatabaseManager;
import backend.Ingestion.CorpusRepository;
import backend.Ingestion.GutenbergIngestor;
import backend.Ingestion.TextSanitizer;
import backend.Database.TransitionData;
import frontend.DbRunner;
import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.LogItem;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.collections.ListChangeListener;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.sql.SQLException;
import java.util.*;

/* Tab 1:
    Makes the text editor and interactive sentence generator on the left and right hand side encapsulating it.
 */
public class EditorView {
    private final AppState state;
    private final TextArea editor = new TextArea();
    private final Random random = new Random();
    //runner object to call all DB transactions on a background thread to not freeze the app
    private final DbRunner dbRunner;

    public EditorView(AppState state) {
        this.state = state;
        this.dbRunner = new DbRunner(state);
    }

    public Node build() {
        editor.setWrapText(true);
        editor.getStyleClass().add("composition");
        editor.setPrefHeight(340);
        editor.setPromptText("Type your composition here, or generate and add sentences from the right panel...");

        // Oudom Pach OTP220001 11/3/2026 Revised: Added dynamic predictive suggestions and TAB auto-complete
        // Holds the top suggested word so typing TAB automatically chooses it
        final String[] topWordForTab = new String[1];

        ComboBox<String> algorithm = new ComboBox<>(FXCollections.observableArrayList("Weighted Probabilistic", "Temperature"));
        algorithm.getSelectionModel().selectFirst();
        algorithm.setMaxWidth(Double.MAX_VALUE);


        // The editor.text listener watches every character and predicts the next word when space is typed
        //Ayush kafley axk220167 11/12/2026 updated so the lastword uses TextSanitizer's sanitize
        ContextMenu suggestions = new ContextMenu();
        editor.textProperty().addListener((o, old, value) -> {
            suggestions.hide();
            topWordForTab[0] = null;

            if (value != null && value.endsWith(" ") && editor.isFocused()) {
                int caret = editor.getCaretPosition();
                String textBeforeCaret = value.substring(0, Math.min(caret, value.length())).trim();

                if (!textBeforeCaret.isEmpty()) {
                    int lastSpace = textBeforeCaret.lastIndexOf(' ');
                    String lastWord = TextSanitizer.sanitize(lastSpace == -1 ? textBeforeCaret : textBeforeCaret.substring(lastSpace + 1));

                    ArrayList<TransitionData> nextWords = state.getTransitions("Temperature".equals(algorithm.getValue()), lastWord);
                    if (nextWords != null && !nextWords.isEmpty()) {
                        suggestions.getItems().clear();

                        long totalSum = 0;
                        for(TransitionData t : nextWords)
                            totalSum += t.getFrequency();

                        System.out.println(totalSum);


                        topWordForTab[0] = nextWords.get(0).getNextWord();
                        int limit = Math.min(5, nextWords.size());

                        for (int i = 0; i < limit; i++) {
                            TransitionData entry = nextWords.get(i);
                            String choice = entry.getNextWord();
                            System.out.println(choice + " " + entry.getFrequency());
                            double pct = (entry.getFrequency() * 100.0) / Math.max(1, totalSum);

                            MenuItem item = new MenuItem(String.format("%d.  %-15s (%4.1f%%)", i + 1, choice, pct));
                            // Oudom Pach OTP220001 11/19/26 Bug fix: Removed extra trailing space after inserted suggested word
                            item.setOnAction(e -> {
                                editor.insertText(editor.getCaretPosition(), choice);
                                suggestions.hide();
                            });
                            suggestions.getItems().add(item);
                        }
                        suggestions.show(editor, Side.BOTTOM, 20, -120);
                    }
                }
            }
        });

        // Oudom Pach OTP220001 11/19/26 Bug fix: Removed extra trailing space on TAB auto-complete
        // Listens for TAB when suggestions are open to accept the top word; ESC dismisses
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.TAB && suggestions.isShowing() && topWordForTab[0] != null) {
                e.consume();
                suggestions.hide();
                editor.insertText(editor.getCaretPosition(), topWordForTab[0]);
            } else if (e.getCode() == KeyCode.ESCAPE) {
                suggestions.hide();
            }
        });

        //written by Ayush Kafley (axk220167)
        //ingest a full sentence once the user finishes it with (./!/?)
        final int[] ingestedUpTo = {0};
        editor.textProperty().addListener((o, oldV, newV) ->{
            if(newV == null){
                ingestedUpTo[0] = 0;
                return;
            }

            //text changed via add to text or clear
            if(!editor.isFocused()){
                ingestedUpTo[0] = newV.length();
                return;
            }

            //text was delteted
            if(newV.length() < ingestedUpTo[0])
                ingestedUpTo[0] = newV.length();

            //single char typed
            if(oldV == null || newV.length() != oldV.length() + 1)
                return;

            //where was the char inserted
            int i = 0;
            while((i < oldV.length()) && (oldV.charAt(i) == newV.charAt(i)))
                i++;

            //added char was inside already ingested sentence -> change the marker
            if(i < ingestedUpTo[0]){
                ingestedUpTo[0] ++;
                return;
            }

            char c = newV.charAt(i);
            if(c == '.' || c == '!' || c == '?'){
                //check if . means abbriviations and not end of sentence
                if(c == '.' && endsWithAbb(newV, i))
                    return;
                String sentence = newV.substring(ingestedUpTo[0], i + 1); //mark new sentane -> since last ingested
                ingestedUpTo[0] = i + 1;
                if(!sentence.isBlank())
                    selfIngest(List.of(sentence));
            }
        });

        // Live word and character counter styled in white
        Label counter = UIFactory.label("0 words · 0 characters", "helper-text");
        editor.textProperty().addListener((o, a, b) ->
                counter.setText((b == null || b.isBlank() ? 0 : b.trim().split("\\s+").length)
                        + " words · " + (b == null ? 0 : b.length()) + " characters"));

        Label suggestionHint = UIFactory.label(
                state.isDatabaseConnected()
                        ? "Live probabilistic suggestions powered by MySQL corpus."
                        : "Demo suggestions · Connect database or import corpus to train.",
                "helper-text");

        VBox left = UIFactory.panel("Composition",
                editor,
                UIFactory.label("Click/Tab to choose a suggested word, or keep typing to ignore.", "helper-text"),
                suggestionHint,
                counter,
                UIFactory.button("Clear text", "", editor::clear));
        VBox.setVgrow(editor, Priority.ALWAYS);

        // Starting word text field: pick the most common starting word from the database if available
        String defaultStartingWord = state.getWords().stream()
                .max(Comparator.comparingInt(AppState.WordItem::starts))
                .map(AppState.WordItem::text)
                .orElse("it");
        String formattedStart = (defaultStartingWord == null || defaultStartingWord.isEmpty())
                ? "It"
                : Character.toUpperCase(defaultStartingWord.charAt(0)) + defaultStartingWord.substring(1);

        TextField starting = new TextField(formattedStart);
        starting.setPromptText("Starting word");
        // Oudom Pach OTP220001 11/08/26 Revised: Combined DbRunner database random word lookup with in-memory fallback for offline resilience
        Button randomWord = UIFactory.button("Random Word", "", () -> {
            if (state.isDatabaseConnected()) {
                dbRunner.rundb(DatabaseManager::getRandomWord, word -> {
                    if (word == null) {
                        state.setStatus("No words in the DB yet, import text first.");
                    } else {
                        starting.setText(Character.toUpperCase(word.charAt(0)) + word.substring(1));
                    }
                }, "Could not get random word from DB: ");
            } else if (!state.getWords().isEmpty()) {
                String picked = state.getWords().get(random.nextInt(state.getWords().size())).text();
                starting.setText(Character.toUpperCase(picked.charAt(0)) + picked.substring(1));
            }
        });
        //Oudom Pach OTP220001 11/12/2026 Revised: Starting word placement and label
        Label startLabel = new Label("Starting Word");
        startLabel.setMinWidth(Region.USE_PREF_SIZE);
        HBox startRow = new HBox(10, startLabel, starting, randomWord);
        startRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(starting, Priority.ALWAYS);


        Spinner<Double> tempSpinner = new Spinner<>(0, 5, 1.0, 0.1);
        tempSpinner.setEditable(true);
        tempSpinner.setPrefWidth(75);
        tempSpinner.setMinWidth(65);
        tempSpinner.disableProperty().bind(algorithm.valueProperty().isEqualTo("Weighted Probabilistic"));

        // Restrict input to non-negative decimal digits only
        tempSpinner.getEditor().setTextFormatter(new TextFormatter<Double>(change ->
            change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));

        // Commit typed value when focus is lost, revert to 1.0 if empty/invalid
        tempSpinner.focusedProperty().addListener((o, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                try {
                    tempSpinner.commitValue();
                    state.setTemperature(tempSpinner.getValue());
                } catch (Exception ex) {
                    tempSpinner.getValueFactory().setValue(1.0);
                    tempSpinner.getEditor().setText("1.0");
                }
            }
        });
        VBox tempBox = new VBox(5, new Label("Temp"), tempSpinner);

        Spinner<Integer> count = new Spinner<>(1, 25, 5);
        //Oudom Pach OTP220001 11/12/2026 Revised batch count field to type in it.
        count.setEditable(true);
        count.setPrefWidth(75);
        count.setMinWidth(65);

        // Restrict input to digits only
        count.getEditor().setTextFormatter(new TextFormatter<Integer>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));

        // Commit typed value when focus is lost
        count.focusedProperty().addListener((o, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                try {
                    count.commitValue();
                } catch (Exception ex) {
                    count.getValueFactory().setValue(5);
                }
            }
        });

        VBox algoBox = new VBox(5, new Label("Algorithm"), algorithm);
        HBox.setHgrow(algoBox, Priority.ALWAYS);
        VBox countBox = new VBox(5, new Label("Batch Count"), count);

        CheckBox selfIngest = new CheckBox("Self-Ingest generated sentences into DB");
        VBox batch = new VBox(9);
        List<CheckBox> selections = new ArrayList<>();
        List<String> generated = new ArrayList<>();

        // Builds the sentence card rows in the scroll pane
        Runnable render = () -> {
            batch.getChildren().clear();
            selections.clear();

            if (generated.isEmpty()) {
                batch.getChildren().add(UIFactory.label("No sentences generated yet. Click 'Generate Sentences' above to build sentences from your database.", "helper-text"));
                return;
            }

            for (String sentence : generated) {
                CheckBox selected = new CheckBox();
                selections.add(selected);
                Label body = new Label(sentence);
                body.setWrapText(true);
                body.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(body, Priority.ALWAYS);

                Button add = UIFactory.button("Add to Text", "", () -> appendToEditor(sentence));
                add.setMinWidth(Region.USE_PREF_SIZE);

                HBox card = new HBox(10, selected, body, add);
                card.setAlignment(Pos.CENTER_LEFT);
                card.getStyleClass().add("sentence-card");
                batch.getChildren().add(card);
            }
        };
        //Oudom Pach OTP220001 11/12/2026 Added:
        // If the database is connected and has words, generate an initial batch using real Markov probabilities
        if (!state.getWords().isEmpty()) {
            for (int i = 0; i < 5; i++) {
                generated.add(state.generateSentence(starting.getText(), false, 1.0f));
            }
        }
        render.run();

        // Oudom Pach OTP220001 11/3/2026 Revised: Connected sentence generation to Markov algorithm and self-ingestion
        // Logic for generating sentences using the active algorithm
        // Ayush Kafley axk220116 11/18/2026: updated self ingestion and saving to DB
        Button generate = UIFactory.button("Generate Sentences", "primary", () -> {
            try {
                count.commitValue();
                tempSpinner.commitValue();
            } catch (Exception ignored) {}
            generated.clear();

            boolean useTemp = "Temperature".equals(algorithm.getValue());
            float tempVal = tempSpinner.getValue().floatValue();

            for (int i = 0; i < count.getValue(); i++) {
                String sentence = state.generateSentence(starting.getText(), useTemp, tempVal);
                generated.add(sentence);
                state.getHistory().add(new LogItem(sentence, UIFactory.now(), algorithm.getValue()));
            }
            //save to the DB
            List<String> batchSentence = new ArrayList<>(generated);
            String algo = algorithm.getValue();
            boolean ingest = selfIngest.isSelected();
            if(state.isDatabaseConnected()){
                dbRunner.rundb(db -> {
                    db.addGeneratedSentences(batchSentence, algo, ingest);
                    return ingest ? CorpusRepository.ingestSentences(db, batchSentence) : null;
                }, result -> {
                    state.notifiDataChanged(); //signal that the generated sentances have been added to the DB for report
                    if(result == null || result.totalWords() == 0){
                        state.setStatus("Saved " + batchSentence.size() + " generated Sentances");
                        return;
                    }
                    state.applyIngestedWords(result.words(), result.transitions());
                    state.setStatus("Saved " + batchSentence.size() + " generated sentences ");
                }, "Could not save generated sentances: ");
            } else if (ingest)
                selfIngest(batchSentence); //pass to save offline in memory
            render.run();
        });
        //Oudom Pach OTP220001 11/12/2026: Hbox to set alignment
        generate.setMinWidth(Region.USE_PREF_SIZE);

        HBox controls = new HBox(10, algoBox, tempBox, countBox, generate);
        controls.setAlignment(Pos.BOTTOM_LEFT);

        ScrollPane list = new ScrollPane(batch);
        list.setFitToWidth(true);
        list.setPrefHeight(240);
        VBox.setVgrow(list, Priority.ALWAYS);

        HBox actions = new HBox(10,
                UIFactory.button("Add Selected to Body of Text", "", () -> {
                    for (int i = 0; i < generated.size(); i++) {
                        if (i < selections.size() && selections.get(i).isSelected()) {
                            appendToEditor(generated.get(i));
                        }
                    }
                }),
                //Oudom Pach OTP220001 11/12/2026 fix alignment of button to make it spaced
                UIFactory.spacer(),
                UIFactory.button("Add All to Body of Text", "", () -> appendToEditor(String.join(" ", generated))));
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox right = UIFactory.panel("Sentence generator",
                startRow, controls, selfIngest,
                UIFactory.label("GENERATED BATCH", "eyebrow"), list, actions);

        SplitPane split = new SplitPane(left, right);
        split.setDividerPositions(0.60);

        return UIFactory.page(
                "From a single word to something more.",
                "Write naturally, explore what comes next, or let probability lead the way.",
                split);
    }

    private void appendToEditor(String sentence) {
        editor.appendText((editor.getText().endsWith(" ") || editor.getText().isEmpty() ? "" : " ") + sentence + " ");
        state.setStatus("Added to composition");
    }

    //helper method to hand the generated sentances to the runner then update memory on successful saves
    private void selfIngest(List<String> sentances) {
        if(state.isDatabaseConnected()){
            dbRunner.rundb(db -> CorpusRepository.ingestSentences(db, sentances), result -> {
                if(result.totalWords() == 0)
                    return;
                state.applyIngestedWords(result.words(), result.transitions());
                state.setStatus("Self-ingested " + result.totalWords() + " words into the Database");
            }, "Could not self-ingest: ");
        } else {
            //if no DB then keep it in memory only
            try {
                CorpusRepository.SentanceResult result = CorpusRepository.ingestSentences(null, sentances);
                state.applyIngestedWords(result.words(), result.transitions());
                state.setStatus(("Self-ingested " + result.totalWords() + " words into the Database"));
            } catch (SQLException e){
                state.setStatus("Could not self-ingest: " + e.getMessage());
            }
        }
    }

    //helper method to check if the period belongs to a abbrivation
    private static boolean endsWithAbb(String text, int index){
        int start = index;
        while(start > 0 && !Character.isWhitespace(text.charAt(start - 1)))
            start --;
        String word = text.substring(start, index).toLowerCase(Locale.ROOT);
        return GutenbergIngestor.ABBREVIATIONS.contains(word);
    }
}
