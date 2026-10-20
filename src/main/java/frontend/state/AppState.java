// Oudom Pach OTP220001 10/20/2026
package frontend.state;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import java.util.*;

public class AppState {
    // UI View-Models (Records)
    public record WordItem(String text, int total, int starts, int ends, int follows) {}
    public record LogItem(String sentence, String time, String algorithm) {}
    public record CorpusItem(String file, String count, String time) {}
    public record TransitionItem(String word, int count, String probability) {}

    // Observable collections shared across views
    private final ObservableList<WordItem> words = FXCollections.observableArrayList();
    private final ObservableList<LogItem> history = FXCollections.observableArrayList();
    private final ObservableList<CorpusItem> corpora = FXCollections.observableArrayList();
    private final Map<String, Map<String, Integer>> transitions = new HashMap<>();
    private final StringProperty statusText = new SimpleStringProperty("Corpus engine online · Demo data · No database connected");
    private final Random random = new Random();

    public static final String[] SAMPLES = {
            "It is a truth universally acknowledged that a single man in possession of a good fortune must be in want of a wife.",
            "It is not what we say or think that defines us but what we do.",
            "It was a delightful visit and a very agreeable evening.",
            "It is a pleasure to meet a person of such good understanding.",
            "It was impossible not to admire the beauty of the morning."
    };

    public AppState() {
        ingestDemoText(String.join(" ", SAMPLES));
        for (int i = 0; i < SAMPLES.length; i++) {
            history.add(new LogItem(SAMPLES[i], "Today, 10:" + (42 - i), "Weighted Probabilistic"));
        }
        history.add(new LogItem(SAMPLES[0], "Today, 10:48", "Weighted Probabilistic"));
        history.add(new LogItem(SAMPLES[0], "Today, 10:49", "Weighted Probabilistic"));
        corpora.add(new CorpusItem("sample_corpus.txt", "91", "Demo corpus"));
    }

    public ObservableList<WordItem> getWords() { return words; }
    public ObservableList<LogItem> getHistory() { return history; }
    public ObservableList<CorpusItem> getCorpora() { return corpora; }
    public Map<String, Map<String, Integer>> getTransitions() { return transitions; }
    public StringProperty statusTextProperty() { return statusText; }
    public void setStatus(String message) { statusText.set(message); }

    public long countOccurrences(String sentence) {
        return history.stream().filter(l -> l.sentence().equals(sentence)).count();
    }

    // Mock Ingestion (To be replaced by Backend Parser + JDBC later)
    public void ingestDemoText(String text) {
        Map<String, int[]> counts = new TreeMap<>();
        for (WordItem w : words) {
            counts.put(w.text(), new int[]{w.total(), w.starts(), w.ends()});
        }
        for (String sentence : text.split("[.!?]+")) {
            String clean = sentence.toLowerCase().replaceAll("[^\\p{L}\\p{N}' ]", " ").trim();
            if (clean.isEmpty()) continue;
            String[] tokens = clean.split("\\s+");
            for (int i = 0; i < tokens.length; i++) {
                int[] c = counts.computeIfAbsent(tokens[i], k -> new int[3]);
                c[0]++;
                if (i == 0) c[1]++;
                if (i == tokens.length - 1) c[2]++;
                if (i + 1 < tokens.length) {
                    transitions.computeIfAbsent(tokens[i], k -> new HashMap<>()).merge(tokens[i + 1], 1, Integer::sum);
                }
            }
        }
        List<WordItem> updated = new ArrayList<>();
        counts.forEach((w, c) -> updated.add(new WordItem(w, c[0], c[1], c[2], transitions.getOrDefault(w, Map.of()).size())));
        words.setAll(updated);
    }

    // Mock Sentence Generator (To be replaced by Backend Strategy classes later)
    public String generateMockSentence(String first, boolean weighted) {
        String current = first.strip().toLowerCase().split("\\s+")[0];
        if (current.isBlank()) current = "it";
        List<String> result = new ArrayList<>();
        result.add(current);
        for (int i = 0; i < 24; i++) {
            Map<String, Integer> next = transitions.get(current);
            if (next == null || next.isEmpty()) break;
            List<String> keys = new ArrayList<>(next.keySet());
            if (weighted) {
                int pick = random.nextInt(next.values().stream().mapToInt(Integer::intValue).sum());
                for (String key : keys) {
                    pick -= next.get(key);
                    if (pick < 0) {
                        current = key;
                        break;
                    }
                }
            } else {
                current = keys.get(random.nextInt(keys.size()));
            }
            result.add(current);
            WordItem w = null;
            for (WordItem candidate : words) {
                if (candidate.text().equals(current)) {
                    w = candidate;
                    break;
                }
            }
            if (result.size() > 6 && w != null && w.ends() > 0) break;
        }
        String sentence = String.join(" ", result);
        return Character.toUpperCase(sentence.charAt(0)) + sentence.substring(1) + ".";
    }
}