// Oudom Pach OTP220001 10/20/2026
package frontend.views;

import frontend.UIFactory;
import frontend.state.AppState;
import frontend.state.AppState.LogItem;
import javafx.collections.FXCollections;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;

import java.util.*;

/* Tab 1:
    Makes the text editor and interactive sentence generator on the left and right hand side encapsulating it.
 */
public class EditorView {
    private final AppState state;
    private final TextArea editor = new TextArea();
    private final Random random = new Random();

    public EditorView(AppState state) {
        this.state = state;
    }

    public Node build() {
        editor.setWrapText(true);
        editor.getStyleClass().add("composition");
        editor.setPrefHeight(340);
        editor.setPromptText("Type your composition here, or generate and add sentences from the right panel...");

        // Holds the top suggested word so typing TAB automatically chooses it
        final String[] topWordForTab = new String[1];

        // The editor.text listener watches every character and predicts the next word when space is typed
        ContextMenu suggestions = new ContextMenu();
        editor.textProperty().addListener((o, old, value) -> {
            suggestions.hide();
            topWordForTab[0] = null;

            if (value != null && value.endsWith(" ") && editor.isFocused()) {
                int caret = editor.getCaretPosition();
                String textBeforeCaret = value.substring(0, Math.min(caret, value.length())).trim();

                if (!textBeforeCaret.isEmpty()) {
                    int lastSpace = textBeforeCaret.lastIndexOf(' ');
                    String lastWord = (lastSpace == -1 ? textBeforeCaret : textBeforeCaret.substring(lastSpace + 1))
                            .toLowerCase()
                            .replaceAll("[^\\p{L}\\p{N}']", "");

                    Map<String, Integer> nextWords = state.getTransitions().get(lastWord);
                    if (nextWords != null && !nextWords.isEmpty()) {
                        suggestions.getItems().clear();

                        int totalSum = nextWords.values().stream().mapToInt(Integer::intValue).sum();
                        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(nextWords.entrySet());
                        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

                        topWordForTab[0] = sorted.get(0).getKey();
                        int limit = Math.min(5, sorted.size());

                        for (int i = 0; i < limit; i++) {
                            Map.Entry<String, Integer> entry = sorted.get(i);
                            String choice = entry.getKey();
                            double pct = (entry.getValue() * 100.0) / Math.max(1, totalSum);

                            MenuItem item = new MenuItem(String.format("%d.  %-15s (%4.1f%%)", i + 1, choice, pct));
                            item.setOnAction(e -> {
                                editor.insertText(editor.getCaretPosition(), choice + " ");
                                suggestions.hide();
                            });
                            suggestions.getItems().add(item);
                        }
                        suggestions.show(editor, Side.BOTTOM, 20, -120);
                    }
                }
            }
        });

        // Listens for TAB when suggestions are open to accept the top word; ESC dismisses
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.TAB && suggestions.isShowing() && topWordForTab[0] != null) {
                e.consume();
                suggestions.hide();
                editor.insertText(editor.getCaretPosition(), topWordForTab[0] + " ");
            } else if (e.getCode() == KeyCode.ESCAPE) {
                suggestions.hide();
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
        String formattedStart = Character.toUpperCase(defaultStartingWord.charAt(0)) + defaultStartingWord.substring(1);

        TextField starting = new TextField(formattedStart);
        starting.setPromptText("Starting word");
        Button randomWord = UIFactory.button("Random Word", "", () -> {
            if (!state.getWords().isEmpty()) {
                String picked = state.getWords().get(random.nextInt(state.getWords().size())).text();
                starting.setText(Character.toUpperCase(picked.charAt(0)) + picked.substring(1));
            }
        });

        HBox startRow = new HBox(10, starting, randomWord);
        HBox.setHgrow(starting, Priority.ALWAYS);

        ComboBox<String> algorithm = new ComboBox<>(FXCollections.observableArrayList("Weighted Probabilistic", "Random / Alternative"));
        algorithm.getSelectionModel().selectFirst();
        algorithm.setMaxWidth(Double.MAX_VALUE);

        Spinner<Integer> count = new Spinner<>(1, 25, 5);
        count.setPrefWidth(100);

        VBox algoBox = new VBox(7, new Label("Algorithm"), algorithm);
        HBox.setHgrow(algoBox, Priority.ALWAYS);
        HBox controls = new HBox(15, algoBox, new VBox(7, new Label("Batch Count"), count));

        CheckBox selfIngest = new CheckBox("Self-Ingest generated sentences into DB");
        VBox batch = new VBox(9);
        List<CheckBox> selections = new ArrayList<>();
        List<String> generated = new ArrayList<>();

        // If the database is connected and has words, generate an initial batch using real Markov probabilities
        if (!state.getWords().isEmpty()) {
            for (int i = 0; i < 5; i++) {
                generated.add(state.generateMockSentence(starting.getText(), true));
            }
        }

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
                Button add = UIFactory.button("Add to Text", "", () -> appendToEditor(sentence));
                VBox cardContent = new VBox(8, body, add);
                HBox.setHgrow(cardContent, Priority.ALWAYS);
                HBox card = new HBox(10, selected, cardContent);
                card.getStyleClass().add("sentence-card");
                batch.getChildren().add(card);
            }
        };
        render.run();

        // Logic for generating sentences using the active algorithm
        Button generate = UIFactory.button("Generate Sentences", "primary", () -> {
            generated.clear();
            for (int i = 0; i < count.getValue(); i++) {
                String sentence = state.generateMockSentence(starting.getText(), algorithm.getValue().startsWith("Weighted"));
                generated.add(sentence);
                state.getHistory().add(new LogItem(sentence, UIFactory.now(), algorithm.getValue()));
                if (selfIngest.isSelected()) state.selfIngestSentence(sentence);
            }
            render.run();
        });
        generate.setMaxWidth(Double.MAX_VALUE);

        ScrollPane list = new ScrollPane(batch);
        list.setFitToWidth(true);
        list.setPrefHeight(240);
        VBox.setVgrow(list, Priority.ALWAYS);

        HBox actions = new HBox(8,
                UIFactory.button("Add Selected to Body of Text", "", () -> {
                    for (int i = 0; i < generated.size(); i++) {
                        if (i < selections.size() && selections.get(i).isSelected()) {
                            appendToEditor(generated.get(i));
                        }
                    }
                }),
                UIFactory.button("Add All to Body of Text", "", () -> appendToEditor(String.join(" ", generated))));

        VBox right = UIFactory.panel("Sentence generator",
                new Label("Starting Word"), startRow, controls, generate, selfIngest,
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
}
