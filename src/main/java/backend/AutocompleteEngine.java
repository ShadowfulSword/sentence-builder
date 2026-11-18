/*
 *  AutocompleteEngine class
 *
 * Takes the letters the user is typing, binds them to a prefix query, and
 * orders the matches so the most likely completion is first.
 *
 * Written for CS4488.0W1, Sentance-Builder Project,
 * November 12, 2026
 */


package backend;

import backend.Database.DatabaseManager;
import backend.Database.TransitionData;
import backend.Database.WordData;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


//finds words that start with the typed letters and picks an order for them
public class AutocompleteEngine {
    public static final int DEFAULT_LIMIT = 5;
    //how many prefix matches to pull before the ranking step cuts the menu down
    private static final int CANDIDATE_POOL = 50;

    private final DatabaseManager db;

    public AutocompleteEngine(DatabaseManager db)
    {
        this.db = db;
    }

    /**
     * Returns up to five completions for the letters currently being typed.
     * @param previousWord The finished word before the one being typed, or blank at the start of a sentence
     * @param typedPrefix The letters the user has typed for the current word
     * @return The ordered completions, most likely first
     * @DateWritten 11/12/2026
     */
    public List<Suggestion> getSuggestions(String previousWord, String typedPrefix) throws SQLException
    {
        return getSuggestions(previousWord, typedPrefix, DEFAULT_LIMIT);
    }

    /**
     * Finds every stored word that starts with the typed prefix and chooses an order for them.
     * A word that has been seen after previousWord is listed before words that only match the letters.
     * Those context matches are ordered by how often that pair occurred. The rest are ordered by
     * how often the word itself occurs in the corpus.
     * @param previousWord The finished word before the one being typed, or blank when there is no context
     * @param typedPrefix The letters the user has typed for the current word
     * @param limit How many completions to return
     * @return The ordered completions, most likely first
     * @DateWritten 11/12/2026
     */
    public List<Suggestion> getSuggestions(String previousWord, String typedPrefix, int limit) throws SQLException
    {
        if (db == null)
            return new ArrayList<>();

        int menuSize = limit <= 0 ? DEFAULT_LIMIT : limit;
        //letters and digits only, so the bound value is a word prefix rather than raw keystrokes
        String prefix = wordCharacters(typedPrefix);
        if (prefix.isEmpty())
            return new ArrayList<>();

        //pull a wider set than the menu so a rarer contextual match is not dropped before ranking
        int pool = Math.max(menuSize, CANDIDATE_POOL);
        List<WordData> matches = db.findWordsByPrefix(toLikePattern(prefix), pool);

        //pair frequency for next-words that also start with the same letters
        Map<String, Integer> followCounts = followCounts(wordCharacters(previousWord), prefix);

        List<Suggestion> ranked = new ArrayList<>();
        Map<String, WordData> byWord = new HashMap<>();
        for (WordData match : matches) {
            byWord.put(match.getWord(), match);
        }
        //a follower can be the right completion even when it is outside the frequency pool
        for (String follower : followCounts.keySet()) {
            if (!byWord.containsKey(follower)) {
                WordData data = db.getWordData(follower);
                if (data != null)
                    byWord.put(follower, data);
            }
        }

        for (WordData match : byWord.values()) {
            Integer pairCount = followCounts.get(match.getWord());
            if (pairCount != null)
                ranked.add(new Suggestion(match.getWord(), pairCount, true));
            else
                ranked.add(new Suggestion(match.getWord(), match.getTotalFrequency(), false));
        }

        //context matches first, then the higher weight inside each group
        ranked.sort((left, right) -> {
            if (left.followsPreviousWord() != right.followsPreviousWord())
                return left.followsPreviousWord() ? -1 : 1;
            int byWeight = Integer.compare(right.getWeight(), left.getWeight());
            if (byWeight != 0)
                return byWeight;
            return left.getWord().compareTo(right.getWord());
        });

        if (ranked.size() <= menuSize)
            return ranked;
        return new ArrayList<>(ranked.subList(0, menuSize));
    }

    /**
     * One suggestion in the menu: the word, the weight used to place it, and whether that
     * weight came from a transition off the previous word.
     * @DateWritten 11/12/2026
     */
    public static class Suggestion {
        private final String word;
        private final int weight;
        private final boolean followsPreviousWord;

        public Suggestion(String word, int weight, boolean followsPreviousWord)
        {
            this.word = word;
            this.weight = weight;
            this.followsPreviousWord = followsPreviousWord;
        }

        public String getWord() { return word; }
        public int getWeight() { return weight; }
        public boolean followsPreviousWord() { return followsPreviousWord; }
    }

    //next-word counts for followers that start with prefix. an unknown previous word yields no pairs
    private Map<String, Integer> followCounts(String previousWord, String prefix)
    {
        if (previousWord.isEmpty())
            return Collections.emptyMap();

        ArrayList<TransitionData> transitions = db.getTransitions(previousWord);
        if (transitions == null || transitions.isEmpty())
            return Collections.emptyMap();

        Map<String, Integer> counts = new HashMap<>();
        for (TransitionData transition : transitions) {
            String next = transition.getNextWord();
            if (next != null && next.startsWith(prefix))
                counts.put(next, transition.getFrequency());
        }
        return counts;
    }

    //keeps letters and digits and lowercases them, matching the words stored by the ingestor
    private static String wordCharacters(String text)
    {
        if (text == null || text.isEmpty())
            return "";

        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c))
                cleaned.append(Character.toLowerCase(c));
        }
        return cleaned.toString();
    }

    //builds the LIKE pattern. % and _ typed by the user stay literal; the final % means "anything after this prefix"
    private static String toLikePattern(String prefix)
    {
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c == '\\' || c == '%' || c == '_')
                pattern.append('\\');
            pattern.append(c);
        }
        pattern.append('%');
        return pattern.toString();
    }
}
