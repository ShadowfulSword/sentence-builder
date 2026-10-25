// Oudom Pach OTP220001 10/20/2026
package frontend.state;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.*;
import backend.Token;

/*
Observer Method Pattern:
this acts as the shared reactive state for the UI. The user can do many things like import txt, generate sentence on tab 1, edit word counts tab 2, and audit duplicates tab 3.
all views will need to access the same data

 */
public class AppState {
    // UI View-Models (Records) will generate constructors,getters
    // WordItem stores the word string, total count, sentence-start count, sentence-end count, and number of unique follow-words
    public record WordItem(String text, int total, int starts, int ends, int follows) {}
    // LogItem stores a generated sentence, the timestamp it was created, and the algorithm used
    public record LogItem(String sentence, String time, String algorithm) {}
    // CorpusItem stores an imported .txt filename, how many words it contributed, and when it was imported
    public record CorpusItem(String file, String count, String time) {}
    // TransitionItem stores a follow-word, how many times it followed the selected word, and its percentage probability
    public record TransitionItem(String word, int count, String probability) {}

    // Observable collections shared across views uses observable list to immediately change events when word is added, edited, or removed. No refresh of ui
    private final ObservableList<WordItem> words = FXCollections.observableArrayList();
    private final ObservableList<LogItem> history = FXCollections.observableArrayList();
    private final ObservableList<CorpusItem> corpora = FXCollections.observableArrayList();
    // Nested map tracking word transitions: Outer key = current word, Inner map = (following word -> pair frequency count)
    private final Map<String, Map<String, Integer>> transitions = new HashMap<>();
    private final StringProperty statusText = new SimpleStringProperty("Corpus engine online · Demo data · No database connected"); // shows state at footer
    private final Random random = new Random();

    public static final String[] SAMPLES = { //using sample strings for now
            "It is a truth universally acknowledged that a single man in possession of a good fortune must be in want of a wife.",
            "It is not what we say or think that defines us but what we do.",
            "It was a delightful visit and a very agreeable evening.",
            "It is a pleasure to meet a person of such good understanding.",
            "It was impossible not to admire the beauty of the morning."
    };

    // Constructor initializes the app with sample sentences, demo history logs, and a placeholder corpus entry
    public AppState() {
        ingestDemoText(String.join(" ", SAMPLES)); //will need to integrate and change injestion
        // Populate initial generation history with the sample sentences
        for (int i = 0; i < SAMPLES.length; i++) {
            history.add(new LogItem(SAMPLES[i], "Today, 10:" + (42 - i), "Weighted Probabilistic"));
        }
        // Add two intentional duplicates of the first sample sentence so Tab 3's duplicate detector has demo data
        history.add(new LogItem(SAMPLES[0], "Today, 10:48", "Weighted Probabilistic"));
        history.add(new LogItem(SAMPLES[0], "Today, 10:49", "Weighted Probabilistic"));
        // Add a sample imported file entry for Tab 3's corpus table
        corpora.add(new CorpusItem("sample_corpus.txt", "91", "Demo corpus"));
    }

    // Getters allowing the 3 Tab views and MainApp to read and observe the shared state
    public ObservableList<WordItem> getWords() { return words; }
    public ObservableList<LogItem> getHistory() { return history; }
    public ObservableList<CorpusItem> getCorpora() { return corpora; }
    public Map<String, Map<String, Integer>> getTransitions() { return transitions; }
    public StringProperty statusTextProperty() { return statusText; }
    // Updates the footer status bar message across the application
    public void setStatus(String message) { statusText.set(message); }

    // Counts how many times an exact sentence string appears in the generation history (used for duplicate detection)
    public long countOccurrences(String sentence) {
        return history.stream().filter(l -> l.sentence().equals(sentence)).count();
    }

    // Mock Ingestion (To be replaced by Backend Parser + JDBC later)
    public void ingestDemoText(String text) {
        // TreeMap keeps words sorted alphabetically while storing [0]=total, [1]=starts, [2]=ends
        Map<String, int[]> counts = new TreeMap<>();
        // Copy existing word counts so importing a new file adds to current stats instead of wiping them
        for (WordItem w : words) {
            counts.put(w.text(), new int[]{w.total(), w.starts(), w.ends()});
        }
        // Split the raw text into individual sentences whenever a period, exclamation mark, or question mark is found
        for (String sentence : text.split("[.!?]+")) {
            // Convert to lowercase and strip out punctuation/symbols (keeping letters, numbers, apostrophes, and spaces)
            String clean = sentence.toLowerCase().replaceAll("[^\\p{L}\\p{N}' ]", " ").trim();
            if (clean.isEmpty()) continue;
            // Split the cleaned sentence into an array of individual words
            String[] tokens = clean.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                // Get or create the [total, starts, ends] count array for this word
                int[] c = counts.computeIfAbsent(tokens[i], k -> new int[3]);
                c[0]++; // Increment total frequency count
                if (i == 0) c[1]++; // If first word in the sentence, increment start count
                if (i == tokens.length - 1) c[2]++; // If last word in the sentence, increment end count
                // If there is a next word in the sentence, record the transition pair (tokens[i] -> tokens[i + 1])
                if (i + 1 < tokens.length) {
                    transitions.computeIfAbsent(tokens[i], k -> new HashMap<>()).merge(tokens[i + 1], 1, Integer::sum);
                }
            }
        }
        // Convert the updated counts map back into WordItem records and refresh the observable words list
        List<WordItem> updated = new ArrayList<>();
        counts.forEach((w, c) -> updated.add(new WordItem(w, c[0], c[1], c[2], transitions.getOrDefault(w, Map.of()).size())));
        words.setAll(updated);
    }

    // Mock Sentence Generator (To be replaced by Backend Strategy classes later)
    // will just use basic calculations of the sum of all follow word counts and picks next word will use our actual algorithim instead
    public String generateMockSentence(String first, boolean weighted) {
        // Clean the user's starting word (take the first word in lowercase, or default to "it" if blank)
        String current = first.strip().toLowerCase().split("\\s+")[0];
        if (current.isBlank()) current = "it";
        List<String> result = new ArrayList<>();
        result.add(current);
        // Generate up to 24 additional words for the sentence
        for (int i = 0; i < 24; i++) {
            // Look up all possible following words for the current word
            Map<String, Integer> next = transitions.get(current);
            if (next == null || next.isEmpty()) break; // Stop if no follow-words exist
            List<String> keys = new ArrayList<>(next.keySet());
            if (weighted) {
                // Weighted Probabilistic: pick a random number up to the total transition count and subtract weights until < 0
                int pick = random.nextInt(next.values().stream().mapToInt(Integer::intValue).sum());
                for (String key : keys) {
                    pick -= next.get(key);
                    if (pick < 0) {
                        current = key;
                        break;
                    }
                }
            } else {
                // Random / Alternative: pick any follow-word with equal probability
                current = keys.get(random.nextInt(keys.size()));
            }
            result.add(current);
            // Check if the newly chosen word has been seen at the end of a sentence before
            WordItem w = null;
            for (WordItem candidate : words) {
                if (candidate.text().equals(current)) {
                    w = candidate;
                    break;
                }
            }
            // Naturally end the sentence once it is over 6 words long and lands on a valid sentence-ending word
            if (result.size() > 6 && w != null && w.ends() > 0) break;
        }
        // Join the chosen words with spaces, capitalize the first letter, and add a period at the end
        String sentence = String.join(" ", result);
        return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1) + ".";
    }

    // In AppState.java:
    public void ingestCorpusStream(Iterator<Token> tokenizer) {
        Map<String, int[]> counts = new TreeMap<>();
        // Preserve existing counts from previous imports
        for (WordItem w : words) {
            counts.put(w.text(), new int[]{w.total(), w.starts(), w.ends()});
        }

        String previousWord = null;
        int wordCount = 0;

        while (tokenizer.hasNext()) {
            Token token = tokenizer.next();
            String word = token.getWord().toLowerCase();
            if (word.isEmpty()) continue;

            wordCount++;
            int[] c = counts.computeIfAbsent(word, k -> new int[3]);
            c[0]++; // Total frequency

            if (token.isStart()) {
                c[1]++; // Sentence start count
                previousWord = null; // Reset previous word at sentence boundary
            }
            if (token.isEnd()) {
                c[2]++; // Sentence end count
            }

            // Build bi-gram transitions
            if (previousWord != null) {
                transitions.computeIfAbsent(previousWord, k -> new HashMap<>())
                        .merge(word, 1, Integer::sum);
            }
            previousWord = word;
        }

        // Refresh the observable words list for Tab 2 and Tab 3
        List<WordItem> updated = new ArrayList<>();
        counts.forEach((w, c) -> updated.add(new WordItem(w, c[0], c[1], c[2], transitions.getOrDefault(w, Map.of()).size())));
        words.setAll(updated);
    }
}