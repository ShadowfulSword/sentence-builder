// Oudom Pach OTP220001 11/17/2026 In-memory DatabaseManager test double for generative algorithm unit testing
package backend;

import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// In-memory test double for DatabaseManager that provides word and transition data
// to ProbabilisticGenerator and TemperatureGenerator without requiring a running MySQL instance.
public class TestDatabaseManager extends DatabaseManager {
    private final Map<String, WordData> words = new HashMap<>();
    private final Map<String, List<TransitionData>> transitions = new HashMap<>();

    public TestDatabaseManager() {
        super();
    }

    public void addWord(WordData word) {
        words.put(word.getWord(), word);
    }

    public void addTransition(String fromWord, String nextWord, int frequency) {
        transitions.computeIfAbsent(fromWord, k -> new ArrayList<>())
                .add(new TransitionData(fromWord, nextWord, frequency));
    }

    @Override
    public WordData getWordData(String word) {
        return words.get(word);
    }

    @Override
    public ArrayList<TransitionData> getTransitions(String from_word) {
        List<TransitionData> list = transitions.get(from_word);
        if (list == null) {
            return null;
        }
        // Return defensive copies so tests and generators can modify frequencies without corrupting backing state
        ArrayList<TransitionData> copy = new ArrayList<>();
        for (TransitionData t : list) {
            copy.add(new TransitionData(t.getWord(), t.getNextWord(), t.getFrequency()));
        }
        return copy;
    }
}
