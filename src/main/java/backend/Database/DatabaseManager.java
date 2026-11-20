/*
 * DatabaseManager Class
 *
 * This class handles all outgoing transactions including updates, add/removes and search queries. It is
 * implemented using AutoCloseable meaning that outside classes can use it with try-with resources and connections
 * to the DB will close on successful executions or on exceptions.
 *
 * Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project,
 * starting October 16, 2026
 *
 * Had class rewritten to reflect a change in the schema, had Gemini handle the rewriting
 * Prompt: can you modify the sql to have the words table have an int as a primary index and the
 * transitions to use that primary key as the foreign key. Can you also then go and modify the
 * databasemanager class to ensure all the interfaces work/output the same things they did before
 * but with the new schema, no mistakes.


 */

package backend.Database;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// The class has AutoCloseable so we can open it from any script and call connection.close() to close it
// or open connections with a try-with-resources to AutoClose
public class DatabaseManager implements AutoCloseable {
    public static final String DB_URL = "jdbc:mysql://localhost:3306/sentence_builder?rewriteBatchedStatements=true";

    // Oudom Pach OTP220001 11/3/2026 Revised: Added default connection constants, ImportedFileRecord, and createDefault factory method
    // Oudom Pach OTP220001 11/08/26 Revised: Reconciled DEFAULT_URL with DB_URL and preserved environment-variable fallback factory
    public static final String DEFAULT_URL = DB_URL;
    public static final String DEFAULT_USER = "root";
    public static final String DEFAULT_PASSWORD = "";

    // Record to represent an imported file row
    public record ImportedFileRecord(int id, String filename, int wordCount, String importDate) {}

    // Factory method to establish a connection using environment variables (SB_DB_URL, SB_DB_USER, SB_DB_PASSWORD) or defaults
    public static DatabaseManager createDefault() throws SQLException {
        String url = System.getenv("SB_DB_URL");
        if (url == null || url.isBlank()) url = DEFAULT_URL;
        String user = System.getenv("SB_DB_USER");
        if (user == null) user = DEFAULT_USER;
        String pword = System.getenv("SB_DB_PASSWORD");
        if (pword == null) pword = DEFAULT_PASSWORD;
        return new DatabaseManager(url, user, pword);
    }

    // The connection object associated with all the functions. Should not be mutable
    private final Connection conn;

    // The batch size of how many words will accumulate before a DB transaction will be made
    private static final int BATCH_SIZE = 5000;

    // Upsert statement for word that either inserts new word or updates on existing word
    private static final String WORD_UPSERT = "INSERT INTO words (word, total_frequency, start_frequency, end_frequency) "
            + "VALUES (?, ?, ?, ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "total_frequency = words.total_frequency + VALUES(total_frequency), "
            + "start_frequency = words.start_frequency + VALUES(start_frequency), "
            + "end_frequency = words.end_frequency + VALUES(end_frequency)";

    // Upsert statement for transition resolving word strings to their integer IDs
    private static final String TRANSITION_UPSERT = "INSERT INTO transitions (word_id, next_word_id, frequency) "
            + "VALUES ((SELECT id FROM words WHERE word = ?), (SELECT id FROM words WHERE word = ?), ?) "
            + "ON DUPLICATE KEY UPDATE "
            + "frequency = transitions.frequency + VALUES(frequency)";

    // Query to fetch outgoing transitions for a given word
    private static final String TRANSITION_GET = "SELECT w.word AS next_word, t.frequency "
            + "FROM transitions t "
            + "JOIN words w ON t.next_word_id = w.id "
            + "WHERE t.word_id = (SELECT id FROM words WHERE word = ?) "
            + "ORDER BY t.frequency DESC";

    // Connection constructor takes in your url, username, and password of your DB connection
    public DatabaseManager(String url, String user, String pword) throws SQLException {
        this.conn = DriverManager.getConnection(url, user, pword);
        System.out.println("Successfully opened connection to DB");
    }

    // Oudom Pach OTP220001 11/17/2026 Revised: Added protected no-arg constructor to enable unit testing and offline test doubles
    protected DatabaseManager() {
        this.conn = null;
    }

    //AutoCloseable's close function overriden to close and inform the close
    @Override
    public void close() throws SQLException {
        if(conn != null && !conn.isClosed())
            conn.close();
        System.out.println("Successfully closed connection to DB");
    }

    // Execute helper method
    private int execute(String query, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++)
                ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    // Take in one word and increment the total_frequency by 1
    // Take in start and end as booleans to tick each of them up by one
    public void addWord(String word, boolean isStart, boolean isEnd) throws SQLException {
        execute(WORD_UPSERT, word, 1, isStart ? 1 : 0, isEnd ? 1 : 0);
    }

    // Use insert into transitions table for new word chains and on already existing word update the total frequency
    // Records 1 occurrence
    public void addTransition(String word, String nextWord) throws SQLException {
        execute(TRANSITION_UPSERT, word, nextWord, 1);
    }

    // Simply add the new file after checking that it is not in the database already
    // Returns true on successful adds
    public boolean addImportedFile(String filename, int wordCount) throws SQLException {
        if (hasImportedFile(filename))
            return false;

        String sql = "INSERT INTO imported_files (filename, wordcount) VALUES (?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filename);
            ps.setInt(2, wordCount);
            ps.executeUpdate();
        }
        return true;
    }

    // Update a single word to be a different word
    // Throws an exception if the new text already belongs to a different word since each word must be unique in the DB
    // Returns true on successful changes, false otherwise
    public boolean updateWordText(String oldWord, String newWord) throws SQLException {
        if (!oldWord.equals(newWord) && hasWord(newWord))
            throw new SQLException("Cannot rename word " + oldWord + ": \"" + newWord + "\" already exists");
        return (execute("UPDATE words SET word = ? WHERE word = ?", newWord, oldWord)) > 0;
    }

    // Update the frequency counts of a specific word
    // Returns true if successful
    public boolean updateWordFrequencies(String word, int totalFrequency, int startFrequency, int endFrequency) throws SQLException {
        String sql_query = "UPDATE words SET total_frequency = ?, start_frequency = ?, end_frequency = ? "
                + "WHERE word = ?";
        return (execute(sql_query, totalFrequency, startFrequency, endFrequency, word)) > 0;
    }

    // Update the frequency of a specific word, nextWord pair
    // Returns true if successful
    public boolean updateTransitionFrequency(String word, String nextWord, int frequency) throws SQLException {
        String sql = "UPDATE transitions SET frequency = ? "
                + "WHERE word_id = (SELECT id FROM words WHERE word = ?) "
                + "AND next_word_id = (SELECT id FROM words WHERE word = ?)";
        return (execute(sql, frequency, word, nextWord)) > 0;
    }

    // Runs a query to check if the DB has an entry for a file with the passed-in filename
    public boolean hasImportedFile(String filename) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM imported_files WHERE filename = ?")) {
            ps.setString(1, filename);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Run a query to check if a passed word exists in the words table
    public boolean hasWord(String word) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM words WHERE word = ?")) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // Removes a passed word, nextWord pair
    public boolean removeTransition(String word, String nextWord) throws SQLException {
        String sql = "DELETE FROM transitions "
                + "WHERE word_id = (SELECT id FROM words WHERE word = ?) "
                + "AND next_word_id = (SELECT id FROM words WHERE word = ?)";
        return (execute(sql, word, nextWord)) > 0;
    }

    // Remove a file from the imported_files table
    public boolean removeImportedFile(int fileID) throws SQLException {
        return (execute("DELETE FROM imported_files WHERE file_id = ?", fileID)) > 0;
    }

    // Setup for an Atomic operation that will only commit if the action successfully completes and roll back if any exception is thrown
    private interface SqlAction {
        void run() throws SQLException;
    }

    // An all-or-nothing commit
    private void inTransaction(SqlAction action) throws SQLException {
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            action.run();
            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    public ArrayList<TransitionData> getTransitions(String from_word) {
        try (PreparedStatement pstmt = conn.prepareStatement(TRANSITION_GET)) {
            pstmt.setString(1, from_word);
            try (ResultSet rs = pstmt.executeQuery()) {
                ArrayList<TransitionData> result = new ArrayList<>();
                while (rs.next())
                    result.add(new TransitionData(from_word, rs.getString("next_word"), rs.getInt("frequency")));
                return result;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public WordData getWordData(String word) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT total_frequency, start_frequency, end_frequency FROM words WHERE word = ?")) {
            pstmt.setString(1, word);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next())
                    return null;
                return new WordData(word, rs.getInt("total_frequency"), rs.getInt("start_frequency"), rs.getInt("end_frequency"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Remove a word from the words table and every associated transition in the transitions table
    public boolean removeWord(String word) throws SQLException {
        boolean[] removed = new boolean[1];
        inTransaction(() -> {
            Integer wordId = null;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM words WHERE word = ?")) {
                ps.setString(1, word);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        wordId = rs.getInt("id");
                    }
                }
            }

            if (wordId != null) {
                execute("DELETE FROM transitions WHERE word_id = ? OR next_word_id = ?", wordId, wordId);
                removed[0] = (execute("DELETE FROM words WHERE id = ?", wordId)) > 0;
            }
        });
        return removed[0];
    }

    // Batch update words and transitions atomically
    public void batchAdd(Collection<WordData> words, Collection<TransitionData> transitions) throws SQLException {
        inTransaction(() -> {
            batchUpsertWords(words);
            batchUpsertTransition(transitions);
        });
    }

    // Take the passed collection of WordData objects and batch upsert them
    private void batchUpsertWords(Collection<WordData> words) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(WORD_UPSERT)) {
            int n = 0;
            for (WordData w : words) {
                ps.setString(1, w.getWord());
                ps.setLong(2, w.getTotalFrequency());
                ps.setLong(3, w.getStartFrequency());
                ps.setLong(4, w.getEndFrequency());

                ps.addBatch();
                if (++n % BATCH_SIZE == 0)
                    ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    // Take the passed collection of TransitionData objects and batch upsert them
    private void batchUpsertTransition(Collection<TransitionData> transitions) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(TRANSITION_UPSERT)) {
            int n = 0;
            for (TransitionData t : transitions) {
                ps.setString(1, t.getWord());
                ps.setString(2, t.getNextWord());
                ps.setInt(3, t.getFrequency());

                ps.addBatch();
                if (++n % BATCH_SIZE == 0)
                    ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    // Oudom Pach OTP220001 11/3/2026 Revised: Added getAllWordData, getAllTransitions, getTransitionsForWord, and getImportedFileRecords
    // Oudom Pach OTP220001 11/08/26 Revised: Reconciled ingestion retrieval methods (getAllWordData, getImportedFileRecords) to coexist with PR #70 on-demand UI queries
    // Retrieve all words stored in the words table as WordData objects
    public List<WordData> getAllWordData() throws SQLException {
        List<WordData> words = new ArrayList<>();
        String sql = "SELECT word, total_frequency, start_frequency, end_frequency FROM words ORDER BY total_frequency DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                words.add(new WordData(
                        rs.getString("word"),
                        rs.getInt("total_frequency"),
                        rs.getInt("start_frequency"),
                        rs.getInt("end_frequency")
                ));
            }
        }
        return words;
    }

    // Retrieve all transitions stored in the transitions table
    public List<TransitionData> getAllTransitions() throws SQLException {
        List<TransitionData> transitions = new ArrayList<>();
        String sql = "SELECT w1.word AS word, w2.word AS next_word, t.frequency "
                + "FROM transitions t "
                + "JOIN words w1 ON w1.id = t.word_id "
                + "JOIN words w2 ON w2.id = t.next_word_id";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                transitions.add(new TransitionData(
                        rs.getString("word"),
                        rs.getString("next_word"),
                        rs.getInt("frequency")
                ));
            }
        }
        return transitions;
    }

    // Retrieve all transitions for a given word
    public List<TransitionData> getTransitionsForWord(String word) throws SQLException {
        List<TransitionData> transitions = new ArrayList<>();
        String sql = "SELECT w1.word AS word, w2.word AS next_word, t.frequency "
                + "FROM transitions t "
                + "JOIN words w1 ON w1.id = t.word_id "
                + "JOIN words w2 ON w2.id = t.next_word_id "
                + "WHERE w1.word = ? "
                + "ORDER BY t.frequency DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    transitions.add(new TransitionData(
                            rs.getString("word"),
                            rs.getString("next_word"),
                            rs.getInt("frequency")
                    ));
                }
            }
        }
        return transitions;
    }

    // Retrieve all imported files from the imported_files table
    public List<ImportedFileRecord> getImportedFileRecords() throws SQLException {
        List<ImportedFileRecord> files = new ArrayList<>();
        String sql = "SELECT file_id, filename, wordcount, import_date FROM imported_files ORDER BY import_date DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp ts = rs.getTimestamp("import_date");
                String dateStr = ts != null ? ts.toString() : "";
                files.add(new ImportedFileRecord(
                        rs.getInt("file_id"),
                        rs.getString("filename"),
                        rs.getInt("wordcount"),
                        dateStr
                ));
            }
        }
        return files;
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Word row, each word followed by its counts and the number of unique follows sorted alphabetically
    public List<WordRow> getAllWords() throws SQLException {
        List<WordRow> rows = new ArrayList<>();
        // Use left join to receive words that are only ever at the end of the sentences and have them set to 0
        String query = """
                SELECT w.word, w.total_frequency, w.start_frequency, w.end_frequency, COUNT(t.next_word_id)
                FROM words w LEFT JOIN transitions t ON t.word_id = w.id
                GROUP BY w.id, w.word, w.total_frequency, w.start_frequency, w.end_frequency
                ORDER BY w.word""";

        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery(query)) {
                while (rs.next()) {
                    rows.add(new WordRow(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                }
            }
        }
        return rows;
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Get every word that follows the passed word in most frequent and alphabetical order
    // Empty list = no follows (end of sentences)
    public List<FollowerRow> getFollowers(String word) throws SQLException {
        List<FollowerRow> rows = new ArrayList<>();
        String query = "SELECT w.word, t.frequency "
                + "FROM transitions t "
                + "JOIN words w ON t.next_word_id = w.id "
                + "WHERE t.word_id = (SELECT id FROM words WHERE word = ?) "
                + "ORDER BY t.frequency DESC, w.word ASC";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, word);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next())
                    rows.add(new FollowerRow(rs.getString(1), rs.getInt(2)));
            }
        }
        return rows;
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Word count, transition count and most frequent starting word
    public CorpusStats getCorpusStats() throws SQLException {
        String query = """
                SELECT (SELECT COUNT(*) FROM words),
                       (SELECT COUNT(*) FROM transitions),
                       (SELECT word FROM words WHERE start_frequency > 0 ORDER BY start_frequency DESC, word ASC LIMIT 1)""";
        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery(query)) {
                rs.next(); // get one row
                return new CorpusStats(rs.getInt(1), rs.getInt(2), rs.getString(3));
            }
        }
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Get every imported file ordered by newest recorded import date
    public List<ImportedFileRow> getImportedFiles() throws SQLException {
        List<ImportedFileRow> rows = new ArrayList<>();
        String query = "SELECT filename, wordcount, import_date FROM imported_files ORDER BY import_date DESC";

        try (Statement s = conn.createStatement()) {
            try (ResultSet rs = s.executeQuery(query)) {
                while (rs.next())
                    rows.add(new ImportedFileRow(rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toLocalDateTime()));
            }
        }
        return rows;
    }

    // Written by Ayush Kafley (axk220167) 11/05/2026
    // Get one random word from the DB or null if the table has nothing in yet
    public String getRandomWord() throws SQLException {
        int totalRow = 0;
        try (Statement s = conn.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM words");
            rs.next();
            totalRow = rs.getInt(1);
        }
        if (totalRow == 0)
            return null;

        int rand = (int) (Math.random() * totalRow);
        try (PreparedStatement ps = conn.prepareStatement("SELECT word FROM words ORDER BY word LIMIT 1 OFFSET ?")) {
            ps.setInt(1, rand);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}