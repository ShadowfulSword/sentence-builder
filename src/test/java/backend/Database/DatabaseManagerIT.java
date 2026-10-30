 // checks for MySQL schema and DatabaseManager writes for GH issue #24

package backend.Database;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


 // each test owns a unique set of rows and removes only those rows afterward
  
public class DatabaseManagerIT {
    private static final String TEST_DATABASE = "sentence_builder_test";
    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/" + TEST_DATABASE
                    + "?rewriteBatchedStatements=true";

    private final String prefix = "it_" + UUID.randomUUID().toString().replace("-", "") + "_";
    private Connection verification;
    private DatabaseManager database;

    // opens a separate read connection so assertions verify committed data
    @BeforeEach
    void connect() throws SQLException {
        String url = environment("SB_TEST_DB_URL", DEFAULT_URL);
        String user = environment("SB_TEST_DB_USER", "root");
        String password = environment("SB_TEST_DB_PASSWORD", "");

        Connection connection = DriverManager.getConnection(url, user, password);
        if (!TEST_DATABASE.equals(connection.getCatalog())) {
            connection.close();
            throw new IllegalArgumentException(
                    "Integration tests must use the " + TEST_DATABASE + " database.");
        }
        verification = connection;
        database = new DatabaseManager(url, user, password);
    }

    // deletes transitions before their referenced words, without clearing other data
    @AfterEach
    void cleanUp() throws SQLException {
        try {
            if (database != null) {
                database.close();
            }
        } finally {
            if (verification != null) {
                try (Connection ignored = verification) {
                    execute("DELETE FROM transitions WHERE LEFT(word, ?) = ? "
                                    + "OR LEFT(next_word, ?) = ?",
                            prefix.length(), prefix, prefix.length(), prefix);
                    execute("DELETE FROM words WHERE LEFT(word, ?) = ?", prefix.length(), prefix);
                    execute("DELETE FROM imported_files WHERE LEFT(filename, ?) = ?",
                            prefix.length(), prefix);
                }
            }
        }
    }

    @Test
    void repeatedWordsAccumulateTotalStartAndEndCounts() throws SQLException {
        String word = word("cat");
        database.addWord(word, true, false);
        database.addWord(word, false, false);
        database.addWord(word, false, true);
        database.addWord(word, true, true);

        assertWordCounts(word, 4, 2, 2);
        assertEquals(1, queryInt("SELECT COUNT(*) FROM words WHERE word = ?", word));
    }

    @Test
    void singleWordSentenceCountsAsBothStartAndEnd() throws SQLException {
        String word = word("hello");
        database.addWord(word, true, true);

        assertWordCounts(word, 1, 1, 1);
    }

    @Test
    void repeatedTransitionsAccumulateInOnePair() throws SQLException {
        String from = word("the");
        String to = word("cat");
        database.addWord(from, true, false);
        database.addWord(to, false, true);

        database.addTransition(from, to);
        database.addTransition(from, to);
        database.addTransition(from, to);

        assertTransitionFrequency(from, to, 3);
        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM transitions WHERE word = ? AND next_word = ?", from, to));
    }

    @Test
    void batchesStoreAggregatedCountsAndIncrementExistingRows() throws SQLException {
        WordData the = new WordData(word("the"), true, false);
        the.addOccurrence(true, false);
        WordData cat = new WordData(word("cat"), false, false);
        cat.addOccurrence(false, false);
        WordData sat = new WordData(word("sat"), false, true);
        WordData ran = new WordData(word("ran"), false, true);
        TransitionData theCat = new TransitionData(the.getWord(), cat.getWord());
        theCat.addFrequency();
        List<WordData> words = List.of(the, cat, sat, ran);
        List<TransitionData> transitions = List.of(theCat,
                new TransitionData(cat.getWord(), sat.getWord()),
                new TransitionData(cat.getWord(), ran.getWord()));

        // reference counts for "the cat sat. the cat ran."
        database.batchAdd(words, transitions);
        assertWordCounts(the.getWord(), 2, 2, 0);
        assertWordCounts(cat.getWord(), 2, 0, 0);
        assertWordCounts(sat.getWord(), 1, 0, 1);
        assertWordCounts(ran.getWord(), 1, 0, 1);
        assertTransitionFrequency(the.getWord(), cat.getWord(), 2);
        assertTransitionFrequency(cat.getWord(), sat.getWord(), 1);
        assertTransitionFrequency(cat.getWord(), ran.getWord(), 1);
        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM transitions WHERE word = ? AND next_word = ?",
                sat.getWord(), the.getWord()));

        database.batchAdd(words, transitions);
        assertWordCounts(the.getWord(), 4, 4, 0);
        assertWordCounts(cat.getWord(), 4, 0, 0);
        assertWordCounts(sat.getWord(), 2, 0, 2);
        assertWordCounts(ran.getWord(), 2, 0, 2);
        assertTransitionFrequency(the.getWord(), cat.getWord(), 4);
        assertTransitionFrequency(cat.getWord(), sat.getWord(), 2);
        assertTransitionFrequency(cat.getWord(), ran.getWord(), 2);
    }

    @Test
    void batchesBeyondFiveThousandKeepTheFinalPartialBatch() throws SQLException {
        // cross the current 5,000-row boundary for both words and transitions.
        int size = 5002;
        List<WordData> words = new ArrayList<>();
        List<TransitionData> transitions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            words.add(new WordData(word("w" + i), i == 0, i == size - 1));
            if (i > 0) {
                transitions.add(new TransitionData(word("w" + (i - 1)), word("w" + i)));
            }
        }

        database.batchAdd(words, transitions);

        assertEquals(size, queryInt(
                "SELECT COUNT(*) FROM words WHERE LEFT(word, ?) = ?", prefix.length(), prefix));
        assertEquals(size - 1, queryInt(
                "SELECT COUNT(*) FROM transitions WHERE LEFT(word, ?) = ?", prefix.length(), prefix));
        assertEquals(size, queryInt(
                "SELECT SUM(total_frequency) FROM words WHERE LEFT(word, ?) = ?",
                prefix.length(), prefix));
        assertEquals(size - 1, queryInt(
                "SELECT SUM(frequency) FROM transitions WHERE LEFT(word, ?) = ?",
                prefix.length(), prefix));
        assertWordCounts(word("w0"), 1, 1, 0);
        assertWordCounts(word("w5000"), 1, 0, 0);
        assertWordCounts(word("w5001"), 1, 0, 1);
        assertTransitionFrequency(word("w4999"), word("w5000"), 1);
        assertTransitionFrequency(word("w5000"), word("w5001"), 1);
    }

    @Test
    void foreignKeyRejectsMissingSourceWord() throws SQLException {
        String destination = word("cat");
        database.addWord(destination, false, true);

        SQLException error = assertThrows(SQLException.class,
                () -> database.addTransition(word("missing"), destination));

        assertIntegrityViolation(error);
        assertEquals(0, queryInt(
                "SELECT COUNT(*) FROM transitions WHERE next_word = ?", destination));
    }

    @Test
    void foreignKeyRejectsMissingDestinationWord() throws SQLException {
        String source = word("the");
        database.addWord(source, true, false);

        SQLException error = assertThrows(SQLException.class,
                () -> database.addTransition(source, word("missing")));

        assertIntegrityViolation(error);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM transitions WHERE word = ?", source));
    }

    @Test
    void failedBatchRollsBackInsertsAndIncrementsAndRestoresAutoCommit() throws SQLException {
        String existing = word("existing");
        String added = word("added");
        database.addWord(existing, true, false);

        assertThrows(SQLException.class, () -> database.batchAdd(
                List.of(new WordData(existing, false, true), new WordData(added, false, true)),
                List.of(new TransitionData(existing, added),
                        new TransitionData(added, word("missing")))));

        assertWordCounts(existing, 1, 1, 0);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM words WHERE word = ?", added));
        assertEquals(0, queryInt("SELECT COUNT(*) FROM transitions WHERE LEFT(word, ?) = ?",
                prefix.length(), prefix));

        // a new write must be visible to the separate connection after rollback.
        String afterFailure = word("after_failure");
        database.addWord(afterFailure, true, true);
        assertWordCounts(afterFailure, 1, 1, 1);
    }

    @Test
    void renamingWordCascadesToBothTransitionForeignKeys() throws SQLException {
        String before = word("before");
        String middle = word("middle");
        String after = word("after");
        String renamed = word("renamed");
        database.addWord(before, true, false);
        database.addWord(middle, false, false);
        database.addWord(after, false, true);
        database.addTransition(before, middle);
        database.addTransition(middle, after);

        assertTrue(database.updateWordText(middle, renamed));

        assertWordCounts(renamed, 1, 0, 0);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM words WHERE word = ?", middle));
        assertTransitionFrequency(before, renamed, 1);
        assertTransitionFrequency(renamed, after, 1);
        assertEquals(0, queryInt("SELECT COUNT(*) FROM transitions WHERE word = ? OR next_word = ?",
                middle, middle));
    }

    @Test
    void importedFileStoresMetadataAndRejectsDuplicateFilename() throws SQLException {
        String filename = word("reference.txt");
        assertTrue(database.addImportedFile(filename, 6));
        assertTrue(database.hasImportedFile(filename));
        assertFalse(database.addImportedFile(filename, 999));

        try (PreparedStatement statement = verification.prepareStatement(
                "SELECT file_id, wordcount, import_date FROM imported_files WHERE filename = ?")) {
            statement.setString(1, filename);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertTrue(result.getInt("file_id") > 0);
                assertEquals(6, result.getInt("wordcount"));
                assertNotNull(result.getTimestamp("import_date"));
                assertFalse(result.next());
            }
        }

        // also check the UNIQUE constraint directly, beyond the API's pre check.
        SQLException error = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO imported_files (filename, wordcount) VALUES (?, ?)", filename, 6));
        assertIntegrityViolation(error);
        assertEquals(1, queryInt(
                "SELECT COUNT(*) FROM imported_files WHERE filename = ?", filename));
    }

    private String word(String value) {
        return prefix + value;
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    private void assertWordCounts(String word, int total, int starts, int ends) throws SQLException {
        try (PreparedStatement statement = verification.prepareStatement(
                "SELECT total_frequency, start_frequency, end_frequency FROM words WHERE word = ?")) {
            statement.setString(1, word);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Missing word: " + word);
                assertArrayEquals(new int[]{total, starts, ends}, new int[]{
                        result.getInt("total_frequency"), result.getInt("start_frequency"),
                        result.getInt("end_frequency")}, "Incorrect counts for " + word);
            }
        }
    }

    private void assertTransitionFrequency(String from, String to, int expected) throws SQLException {
        assertEquals(expected, queryInt(
                "SELECT frequency FROM transitions WHERE word = ? AND next_word = ?", from, to),
                "Incorrect count for " + from + " -> " + to);
    }

    private static void assertIntegrityViolation(SQLException error) {
        assertNotNull(error.getSQLState());
        assertTrue(error.getSQLState().startsWith("23"),
                "Expected an integrity constraint error, got: " + error);
    }

    private int queryInt(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = verification.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next(), "Query returned no row: " + sql);
                return result.getInt(1);
            }
        }
    }

    private void execute(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = verification.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
