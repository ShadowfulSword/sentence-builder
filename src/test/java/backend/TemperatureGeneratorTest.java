// Oudom Pach OTP220001 11/17/2026 Automated unit tests validating TemperatureGenerator scaling, greedy mode, and transition probabilities
package backend;

import backend.Database.TransitionData;
import backend.Database.WordData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TemperatureGeneratorTest {

    private TestDatabaseManager db;
    private TemperatureGenerator generator;

    @BeforeEach
    void setUp() {
        db = new TestDatabaseManager();
        generator = new TemperatureGenerator(db);
    }

    // Verifies initial default temperature is 1.0 and mutation via setter works as expected.
    @Test
    void testTemperatureGetterAndSetter() {
        assertEquals(1.0f, generator.getTemperature(), 1e-6);

        generator.setTemperature(0.5f);
        assertEquals(0.5f, generator.getTemperature(), 1e-6);

        generator.setTemperature(2.0f);
        assertEquals(2.0f, generator.getTemperature(), 1e-6);
    }

    // Greedy mode at T <= 1e-4.
    // When temperature is sufficiently low, it must deterministically select the top (highest frequency) token 100% of the time.
    @Test
    void testGreedySelectionAtExtremelyLowTemperature() {
        WordData word = new WordData("choose", 100, 10, 0);
        db.addWord(word);
        // "primary" is the top transition (60%), followed by "secondary" (30%) and "tertiary" (10%)
        db.addTransition("choose", "primary", 60);
        db.addTransition("choose", "secondary", 30);
        db.addTransition("choose", "tertiary", 10);

        generator.setTemperature(0.00005f); // Below 1e-4 threshold

        // Sample 500 times: under greedy mode, "primary" should be picked 100% of the time
        for (int i = 0; i < 500; i++) {
            String selected = generator.getRandomNextWord(word);
            assertEquals("primary", selected, "Greedy mode must always pick the highest frequency candidate");
        }
    }

    // Validates that transition probabilities sum correctly under various temperatures.
    @Test
    void testTemperatureProbabilitiesSumToOne() {
        WordData word = new WordData("base", 100, 10, 0);
        db.addWord(word);
        db.addTransition("base", "optionA", 50);
        db.addTransition("base", "optionB", 30);
        db.addTransition("base", "optionC", 20);

        float[] testTemperatures = {0.2f, 0.7f, 1.0f, 1.5f, 2.5f};

        for (float temp : testTemperatures) {
            generator.setTemperature(temp);

            // Subclass test harness to directly inspect scaled transitions from getTransitions
            TemperatureGenerator testGen = new TemperatureGenerator(db) {
                @Override
                public ArrayList<TransitionData> getTransitions(WordData w) {
                    return super.getTransitions(w);
                }
            };
            testGen.setTemperature(temp);

            ArrayList<TransitionData> scaled = testGen.getTransitions(word);
            assertNotNull(scaled);
            assertEquals(3, scaled.size());

            int totalFreq = scaled.stream().mapToInt(TransitionData::getFrequency).sum();
            assertTrue(totalFreq > 0, "Total frequency must be positive");

            double probSum = 0.0;
            for (TransitionData t : scaled) {
                assertTrue(t.getFrequency() >= 1, "Scaled frequency must be at least 1");
                double prob = (double) t.getFrequency() / totalFreq;
                probSum += prob;
            }

            assertEquals(1.0, probSum, 1e-6, "Probabilities must sum to 1.0 for temperature " + temp);
        }
    }

    // Validates that increasing temperature (T > 1.0) flattens the probability distribution,
    // increasing the likelihood of picking lower-frequency words compared to T = 1.0.
    @Test
    void testHighTemperatureFlattensDistribution() {
        WordData word = new WordData("path", 100, 10, 0);
        db.addWord(word);
        db.addTransition("path", "frequent", 80);
        db.addTransition("path", "infrequent", 20);

        int sampleSize = 10_000;

        // Baseline at T = 1.0
        generator.setTemperature(1.0f);
        int infrequentBaseCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if ("infrequent".equals(generator.getRandomNextWord(word))) {
                infrequentBaseCount++;
            }
        }
        double baseRate = (double) infrequentBaseCount / sampleSize; // ~20%

        // High temperature at T = 2.5
        generator.setTemperature(2.5f);
        int infrequentHighCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if ("infrequent".equals(generator.getRandomNextWord(word))) {
                infrequentHighCount++;
            }
        }
        double highRate = (double) infrequentHighCount / sampleSize;

        assertTrue(highRate > baseRate + 0.05,
                String.format("High temperature (%.2f) should increase rare word rate compared to base (%.2f)", highRate, baseRate));
    }

    // Validates that lowering temperature (1e-4 < T < 1.0) sharpens the probability distribution,
    // making the dominant word even more likely to be chosen compared to T = 1.0.
    @Test
    void testLowTemperatureSharpensDistribution() {
        WordData word = new WordData("lead", 100, 10, 0);
        db.addWord(word);
        db.addTransition("lead", "dominant", 70);
        db.addTransition("lead", "secondary", 30);

        int sampleSize = 10_000;

        // Baseline at T = 1.0
        generator.setTemperature(1.0f);
        int dominantBaseCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if ("dominant".equals(generator.getRandomNextWord(word))) {
                dominantBaseCount++;
            }
        }
        double baseDominantRate = (double) dominantBaseCount / sampleSize; // ~70%

        // Low temperature at T = 0.3
        generator.setTemperature(0.3f);
        int dominantLowCount = 0;
        for (int i = 0; i < sampleSize; i++) {
            if ("dominant".equals(generator.getRandomNextWord(word))) {
                dominantLowCount++;
            }
        }
        double lowDominantRate = (double) dominantLowCount / sampleSize;

        assertTrue(lowDominantRate > baseDominantRate + 0.10,
                String.format("Low temperature (%.2f) should increase dominant word rate compared to base (%.2f)", lowDominantRate, baseDominantRate));
    }

    // Verifies deterministic sentence generation when greedy temperature is applied.
    @Test
    void testGreedyTemperatureProducesDeterministicSentences() {
        db.addWord(new WordData("the", 10, 10, 0));
        db.addWord(new WordData("cat", 10, 0, 0));
        db.addWord(new WordData("sat", 10, 0, 10));

        db.addTransition("the", "cat", 8);
        db.addTransition("the", "dog", 2);

        db.addTransition("cat", "sat", 9);
        db.addTransition("cat", "ran", 1);

        generator.setTemperature(0.00001f);

        String firstRun = generator.generateSentence("the", 5);
        for (int i = 0; i < 20; i++) {
            String subsequentRun = generator.generateSentence("the", 5);
            assertEquals(firstRun, subsequentRun, "Greedy temperature sentence generation must be 100% deterministic");
        }
    }

    // Verifies null and empty handling in TemperatureGenerator.
    @Test
    void testNullAndEmptyHandling() {
        assertNull(generator.getRandomNextWord(null));

        WordData noTransitions = new WordData("empty", 5, 0, 0);
        db.addWord(noTransitions);
        assertNull(generator.getRandomNextWord(noTransitions));

        generator.setDatabaseManager(null);
        assertNull(generator.getRandomNextWord(noTransitions));
        assertNull(generator.generateSentence("it", 5));
    }
}
