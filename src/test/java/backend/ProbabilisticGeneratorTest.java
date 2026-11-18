// Oudom Pach OTP220001 11/17/2026 Automated unit tests validating ProbabilisticGenerator algorithm and transition probabilities
package backend;

import backend.Database.TransitionData;
import backend.Database.WordData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ProbabilisticGeneratorTest {

    private TestDatabaseManager db;
    private ProbabilisticGenerator generator;

    @BeforeEach
    void setUp() {
        db = new TestDatabaseManager();
        generator = new ProbabilisticGenerator(db);
    }

    // Confirms that transition probabilities sum correctly to 1.0 (100%).
    @Test
    void testTransitionProbabilitiesSumToOne() {
        WordData currentWord = new WordData("the", 100, 20, 0);
        db.addWord(currentWord);
        db.addTransition("the", "cat", 50);
        db.addTransition("the", "dog", 30);
        db.addTransition("the", "bird", 20);

        ArrayList<TransitionData> transitions = db.getTransitions("the");
        assertNotNull(transitions);
        assertEquals(3, transitions.size());

        int totalFrequency = transitions.stream().mapToInt(TransitionData::getFrequency).sum();
        assertEquals(100, totalFrequency);

        double probabilitySum = 0.0;
        for (TransitionData t : transitions) {
            double probability = (double) t.getFrequency() / totalFrequency;
            assertTrue(probability > 0.0 && probability <= 1.0);
            probabilitySum += probability;
        }

        assertEquals(1.0, probabilitySum, 1e-6, "Transition probabilities must sum to 1.0");
    }

    // Validates that next-word selection statistically adheres to transition weights.
    // Given transitions of 80% and 20%, sampling 10,000 words should select words within +/- 3% tolerance.
    @Test
    void testProbabilisticSelectionMatchesRelativeWeights() {
        WordData currentWord = new WordData("quick", 100, 10, 0);
        db.addWord(currentWord);
        db.addTransition("quick", "brown", 80);
        db.addTransition("quick", "silver", 20);

        int sampleSize = 10_000;
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < sampleSize; i++) {
            String selected = generator.getRandomNextWord(currentWord);
            assertNotNull(selected);
            counts.merge(selected, 1, Integer::sum);
        }

        assertEquals(sampleSize, counts.getOrDefault("brown", 0) + counts.getOrDefault("silver", 0));

        double brownPct = (double) counts.get("brown") / sampleSize;
        double silverPct = (double) counts.get("silver") / sampleSize;

        assertEquals(0.80, brownPct, 0.03, "Option with weight 80 should be chosen ~80% of the time (+/- 3%)");
        assertEquals(0.20, silverPct, 0.03, "Option with weight 20 should be chosen ~20% of the time (+/- 3%)");
    }

    // When only one follower transition exists, it must be selected 100% of the time deterministically.
    @Test
    void testSingleTransitionIsDeterministic() {
        WordData word = new WordData("unique", 15, 2, 0);
        db.addWord(word);
        db.addTransition("unique", "path", 15);

        for (int i = 0; i < 100; i++) {
            String selected = generator.getRandomNextWord(word);
            assertEquals("path", selected);
        }
    }

    // Verifies safe null handling across edge cases (null word, missing transitions, empty frequency).
    @Test
    void testEdgeCasesReturnNullSafely() {
        // Null currentWord
        assertNull(generator.getRandomNextWord(null));

        // Word with no transitions in database
        WordData isolatedWord = new WordData("isolated", 5, 0, 5);
        db.addWord(isolatedWord);
        assertNull(generator.getRandomNextWord(isolatedWord));

        // Word with transition frequency of 0
        WordData zeroFreqWord = new WordData("zero", 1, 0, 0);
        db.addWord(zeroFreqWord);
        db.addTransition("zero", "nowhere", 0);
        assertNull(generator.getRandomNextWord(zeroFreqWord));

        // Generator with null DatabaseManager
        generator.setDatabaseManager(null);
        assertNull(generator.getRandomNextWord(isolatedWord));
        assertNull(generator.generateSentence("seed", 10));
    }

    // Verifies sentence formatting: starting capital letter, trailing period, and correct token joining.
    @Test
    void testGenerateSentenceFormattingAndPunctuation() {
        db.addWord(new WordData("the", 5, 5, 0));
        db.addWord(new WordData("sun", 5, 0, 0));
        db.addWord(new WordData("rises", 5, 0, 5));

        db.addTransition("the", "sun", 5);
        db.addTransition("sun", "rises", 5);

        String sentence = generator.generateSentence("the", 5);

        assertNotNull(sentence);
        assertTrue(Character.isUpperCase(sentence.charAt(0)), "First character should be capitalized");
        assertTrue(sentence.endsWith("."), "Sentence should end with a period");
        assertEquals("The sun rises.", sentence);
    }

    // Verifies natural sentence termination: once length exceeds 6 words and an end-frequency word is reached.
    @Test
    void testGenerateSentenceNaturalTerminationAfterSixWords() {
        // Build an 8-word linear chain where word 4 has end_frequency > 0, and word 7 has end_frequency > 0
        String[] chain = {"one", "two", "three", "four", "five", "six", "seven", "eight"};
        for (int i = 0; i < chain.length; i++) {
            int endFreq = (i == 3 || i == 6) ? 5 : 0; // "four" and "seven" can end sentences
            db.addWord(new WordData(chain[i], 10, i == 0 ? 10 : 0, endFreq));
            if (i < chain.length - 1) {
                db.addTransition(chain[i], chain[i + 1], 10);
            }
        }

        // Sentence generation requested for up to 20 words
        String sentence = generator.generateSentence("one", 20);

        // At word "four" (size 4), size <= 6 so it shouldn't stop.
        // At word "seven" (size 7), size > 6 and endFrequency > 0, so it naturally stops at "seven".
        assertNotNull(sentence);
        assertEquals("One two three four five six seven.", sentence);
    }

    // Verifies that sentence generation respects the max length parameter when no end word is encountered.
    @Test
    void testGenerateSentenceRespectsMaximumLength() {
        // Cycle between "tick" and "tock" with 0 end frequency
        db.addWord(new WordData("tick", 10, 0, 0));
        db.addWord(new WordData("tock", 10, 0, 0));
        db.addTransition("tick", "tock", 10);
        db.addTransition("tock", "tick", 10);

        int maxAdditionalWords = 4;
        String sentence = generator.generateSentence("tick", maxAdditionalWords);

        // Total words = seed word (1) + maxAdditionalWords (4) = 5 words
        String[] tokens = sentence.replace(".", "").split("\\s+");
        assertEquals(5, tokens.length);
    }

    // Verifies seed word sanitization: null/blank defaults to "it", multi-word seeds use the first token.
    @Test
    void testSeedWordSanitization() {
        db.addWord(new WordData("it", 5, 5, 0));
        db.addWord(new WordData("hello", 5, 5, 0));

        // Null seed defaults to "it"
        String sentenceNull = generator.generateSentence(null, 0);
        assertEquals("It.", sentenceNull);

        // Blank seed defaults to "it"
        String sentenceBlank = generator.generateSentence("   ", 0);
        assertEquals("It.", sentenceBlank);

        // Multi-word seed uses only the first word
        String sentenceMulti = generator.generateSentence("hello world again", 0);
        assertEquals("Hello.", sentenceMulti);
    }

    // Verifies that generation stops cleanly when reaching a word with no transitions without throwing exceptions.
    @Test
    void testDeadEndTermination() {
        db.addWord(new WordData("start", 5, 5, 0));
        db.addWord(new WordData("deadend", 5, 0, 0));
        db.addTransition("start", "deadend", 5);
        // "deadend" has no transitions

        String sentence = generator.generateSentence("start", 10);
        assertNotNull(sentence);
        assertEquals("Start deadend.", sentence);
    }
}
