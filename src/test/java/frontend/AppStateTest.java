// Oudom Pach OTP220001 11/3/2026 Unit tests for AppState in-memory data structures and corpus ingestion
package frontend;

import backend.Database.TransitionData;
import backend.Database.WordData;
import frontend.state.AppState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AppStateTest {

    private AppState state;

    @BeforeEach
    void setUp() {
        state = new AppState();
    }

    @Test
    void testApplyIngestedCorpusMergesWordsAndTransitions() {
        WordData w1 = new WordData("whale", 10, 3, 2);
        WordData w2 = new WordData("ocean", 5, 1, 1);
        TransitionData t1 = new TransitionData("whale", "ocean", 4);

        int initialCorporaSize = state.getCorpora().size();
        state.applyIngestedCorpus("Moby Dick Test", 15, 1024, List.of(w1, w2), List.of(t1));

        assertEquals(initialCorporaSize + 1, state.getCorpora().size());
        assertEquals("Moby Dick Test", state.getCorpora().get(0).file());

        // Verify words list contains "whale"
        assertTrue(state.getWords().stream().anyMatch(w -> w.text().equals("whale") && w.total() >= 10));
        assertTrue(state.getWords().stream().anyMatch(w -> w.text().equals("ocean") && w.total() >= 5));

        // Verify transition map contains whale -> ocean
        assertTrue(state.getTransitions().containsKey("whale"));
        assertEquals(4, state.getTransitions().get("whale").get("ocean"));
    }

    @Test
    void testSelfIngestSentenceUpdatesWordsAndTransitions() {
        String sentence = "Whales swim fast.";
        state.selfIngestSentence(sentence);

        assertTrue(state.getWords().stream().anyMatch(w -> w.text().equals("whales")));
        assertTrue(state.getWords().stream().anyMatch(w -> w.text().equals("swim")));
        assertTrue(state.getWords().stream().anyMatch(w -> w.text().equals("fast")));

        assertTrue(state.getTransitions().containsKey("whales"));
        assertTrue(state.getTransitions().get("whales").containsKey("swim"));
    }

    @Test
    void testWordDataAndTransitionDataConstructors() {
        WordData word = new WordData("ship", 15, 4, 3);
        assertEquals("ship", word.getWord());
        assertEquals(15, word.getTotalFrequency());
        assertEquals(4, word.getStartFrequency());
        assertEquals(3, word.getEndFrequency());

        TransitionData transition = new TransitionData("great", "white", 8);
        assertEquals("great", transition.getWord());
        assertEquals("white", transition.getNextWord());
        assertEquals(8, transition.getFrequency());
    }

    @Test
    void testGenerateMockSentenceProducesValidSentence() {
        WordData w1 = new WordData("whale", 10, 5, 0);
        WordData w2 = new WordData("ocean", 5, 0, 5);
        TransitionData t1 = new TransitionData("whale", "ocean", 4);
        state.applyIngestedCorpus("Test Corpus", 15, 1024, List.of(w1, w2), List.of(t1));

        String sentence = state.generateMockSentence("whale", true);
        assertNotNull(sentence);
        assertTrue(sentence.startsWith("Whale"));
        assertTrue(sentence.endsWith("."));

        // Test with null or empty seed
        String defaultSentence = state.generateMockSentence(null, true);
        assertNotNull(defaultSentence);
        assertTrue(defaultSentence.startsWith("It"));
        assertTrue(defaultSentence.endsWith("."));
    }
}
