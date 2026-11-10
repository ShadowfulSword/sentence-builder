/* ****************************************************************
 * GutenbergIngestorTest Class
 *
 * This class tests Gutenberg file ingestion and tokenization using
 * temporary files. It verifies metadata, reading progress, token
 * order, sentence boundaries, and end-of-file handling.
 *
 * Added by Yaseen Hafid (yxh190036) for CS4485.0W1, Sentence-Builder Project,
 * on November 6, 2026 as part of issue #18
 * ****************************************************************/

package backend.Ingestion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

// Exercise the real Gutenberg parser and tokenizer using temporary files (yaseen)
class GutenbergIngestorTest {
    private static final String HEADER = """
            Title: Tokenizer Fixture
            Author: Test Author
            Release date: November 6, 2026 [eBook #12345]

            *** START OF THE PROJECT GUTENBERG EBOOK TOKENIZER FIXTURE ***

            """;
    private static final String FOOTER =
            "*** END OF THE PROJECT GUTENBERG EBOOK TOKENIZER FIXTURE ***\n";

    @TempDir
    Path directory;

    private final GutenbergIngestor ingestor = new GutenbergIngestor();

    // #18: Close the reader before JUnit deletes the temporary file (yaseen)
    @AfterEach
    void closeFile() throws IOException {
        ingestor.close();
    }

    // #18: Check header fields, reading progress, and the exact body token sequence (yaseen)
    @Test
    void readsMetadataAndEmitsOnlyBodyTokensWithSentenceBoundaries() throws IOException {
        Path file = openFile(HEADER + "The\tcat sat. THE cat ran!\n\n" + FOOTER + "License text.\n");
        assertTrue(ingestor.extractMetadata());

        DocumentMetadata metadata = ingestor.getMetadata();
        assertEquals("Tokenizer Fixture", metadata.getTitle());
        assertEquals("Test Author", metadata.getAuthor());
        assertEquals("November 6, 2026", metadata.getDate());
        assertEquals(12345, metadata.getBookNumber());
        assertEquals(Files.size(file), metadata.getSizeBytes());
        long headerProgress = ingestor.getBytesRead();

        CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
        assertTrue(tokenizer.hasNext());
        assertTrue(tokenizer.hasNext());
        assertToken(tokenizer.next(), "the", 0, true, false);
        assertToken(tokenizer.next(), "cat", 1, false, false);
        assertToken(tokenizer.next(), "sat", 2, false, true);
        assertToken(tokenizer.next(), "the", 0, true, false);
        assertToken(tokenizer.next(), "cat", 1, false, false);
        assertToken(tokenizer.next(), "ran", 2, false, true);
        assertFalse(tokenizer.hasNext());
        long completedProgress = ingestor.getBytesRead();
        assertTrue(completedProgress > headerProgress);
        assertFalse(tokenizer.hasNext());
        assertThrows(NoSuchElementException.class, tokenizer::next);
        assertEquals(completedProgress, ingestor.getBytesRead());
    }

    //checks

    // #18: Check that "Dr." stays in the first sentence and appears once (yaseen)
    @Test
    void keepsAnAbbreviationInsideItsSentenceWithoutRepeatingIt() throws IOException {
        openFile(HEADER + "Dr. Rivera spoke. Next!\n\n" + FOOTER + "\n");
        assertTrue(ingestor.extractMetadata());

        CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
        assertToken(tokenizer.next(), "dr", 0, true, false);
        assertToken(tokenizer.next(), "rivera", 1, false, false);
        assertToken(tokenizer.next(), "spoke", 2, false, true);
        assertToken(tokenizer.next(), "next", 0, true, true);
        assertFalse(tokenizer.hasNext());
    }

    // #18: check a final sentence with no footer or trailing newline (yaseen)
    @Test
    void readsLastSentenceAtPhysicalEndOfFile() throws IOException {
        openFile(HEADER + "One sentence.");
        assertTrue(ingestor.extractMetadata());

        CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
        assertToken(tokenizer.next(), "one", 0, true, false);
        assertToken(tokenizer.next(), "sentence", 1, false, true);
        assertFalse(tokenizer.hasNext());
    }

    // #18: check an empty corpus that has a valid header and footer (yaseen)
    @Test
    void reportsNoTokensForAnEmptyBody() throws IOException {
        openFile(HEADER + FOOTER + "\n");
        assertTrue(ingestor.extractMetadata());

        CorpusTokenizer tokenizer = new CorpusTokenizer(ingestor);
        assertFalse(tokenizer.hasNext());
        assertThrows(NoSuchElementException.class, tokenizer::next);
    }

    // #18: an incomplete header should produce a false result (yaseen)
    @Test
    void rejectsAFileWithoutACompleteGutenbergHeader() throws IOException {
        openFile("Title: Incomplete fixture\n");
        assertFalse(ingestor.extractMetadata());
    }

    // #18: A missing path should produce a false result (yaseen)
    @Test
    void reportsThatAMissingFileCouldNotBeOpened() {
        assertFalse(ingestor.openFile(directory.resolve("missing.txt").toString()));
    }

    private Path openFile(String contents) throws IOException {
        Path file = Files.writeString(directory.resolve("fixture.txt"), contents);
        assertTrue(ingestor.openFile(file.toString()));
        return file;
    }

    private static void assertToken(Token token, String word, int index, boolean start, boolean end) {
        assertNotNull(token);
        assertEquals(word, token.getWord());
        assertEquals(index, token.getIndex(), "position of " + word);
        assertEquals(start, token.isStart(), "sentence start for " + word);
        assertEquals(end, token.isEnd(), "sentence end for " + word);
    }
}