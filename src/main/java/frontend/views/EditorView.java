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

public class EditorView {
    private final AppState state;
    private final TextArea editor = new TextArea("It is a truth universally acknowledged that a single man ");
    private final Random random = new Random();

    public EditorView(AppState state) {
        this.state = state;
    }

    public Node build() {
        editor.setWrapText(true);
        editor.getStyleClass().add("composition");
        editor.setPrefHeight(340);

        ContextMenu suggestions = new ContextMenu();
        editor.textProperty().addListener((o, old, value) -> {
            suggestions.hide();
            if (value.endsWith(" ") && editor.isFocused()) {
                suggestions.getItems().clear();
                String[] choices = {"in", "must", "who", "of"};
                String[] probabilities = {"42%", "21%", "14%", "9%"};
                for (int i = 0; i < choices.length; i++) {
                    String choice = choices[i];
                    MenuItem item = new MenuItem((i + 1) + ".  " + choice + "                  " + probabilities[i]);
                    item.setOnAction(e -> editor.insertText(editor.getCaretPosition(), choice + " "));
                    suggestions.getItems().add(item);
                }
                suggestions.show(editor, Side.BOTTOM, 20, -120);
            }
        });

        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.TAB && suggestions.isShowing()) {
                e.consume();
                suggestions.hide();
                editor.insertText(editor.getCaretPosition(), "in ");
            } else if (e.getCode() == KeyCode.ESCAPE) {
                suggestions.hide();
            }
        });

        Label counter = UIFactory.label("11 words", "muted");
        editor.textProperty().addListener((o, a, b) ->
                counter.setText((b.isBlank() ? 0 : b.trim().split("\\s+").length) + " words · " + b.length() + " characters"));

        VBox left = UIFactory.panel("Composition",
                editor,
                UIFactory.label("Click/Tab to choose a word, or keep typing to ignore.", "muted"),
                UIFactory.label("Demo suggestions · Import a corpus to train the generator.", "muted"),
                counter,
                UIFactory.button("Clear text", "", editor::clear));
        VBox.setVgrow(editor, Priority.ALWAYS);

        TextField starting = new TextField("It");
        starting.setPromptText("Starting word");
        Button randomWord = UIFactory.button("Random Word", "", () -> {
            if (!state.getWords().isEmpty()) {
                starting.setText(state.getWords().get(random.nextInt(state.getWords().size())).text());
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

        CheckBox selfIngest = new CheckBox("Self-Ingest generated sentences into DB (local demo)");
        VBox batch = new VBox(9);
        List<CheckBox> selections = new ArrayList<>();
        List<String> generated = new ArrayList<>(Arrays.asList(AppState.SAMPLES));

        Runnable render = () -> {
            batch.getChildren().clear();
            selections.clear();
            for (String sentence : generated) {
                CheckBox selected = new CheckBox();
                selections.add(selected);
                Label body = new Label(sentence);
                body.setWrapText(true);
                body.setMaxWidth(Double.MAX_VALUE);
                Button add = UIFactory.button("Add to Text", "", () -> appendToEditor(sentence));
                VBox content = new VBox(8, body, add);
                HBox.setHgrow(content, Priority.ALWAYS);
                HBox card = new HBox(10, selected, content);
                card.getStyleClass().add("sentence-card");
                batch.getChildren().add(card);
            }
        };
        render.run();

        Button generate = UIFactory.button("Generate Sentences", "primary", () -> {
            generated.clear();
            for (int i = 0; i < count.getValue(); i++) {
                String sentence = state.generateMockSentence(starting.getText(), algorithm.getValue().startsWith("Weighted"));
                generated.add(sentence);
                state.getHistory().add(new LogItem(sentence, UIFactory.now(), algorithm.getValue()));
                if (selfIngest.isSelected()) state.ingestDemoText(sentence);
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
                        if (selections.get(i).isSelected()) appendToEditor(generated.get(i));
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
        editor.appendText((editor.getText().endsWith(" ") ? "" : " ") + sentence + " ");
        state.setStatus("Added to composition");
    }
}