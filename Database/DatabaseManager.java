/*
* DatabaseManager Class
*
* This class handles all outgoing transactions including updates, add/removes and search queries. It is
* implemented using AutoCloseable meaning that outside classes can use it with try-with resources and connections
* to the DB will close on successful executions or on exceptions.
*
* Written by Ayush Kafley (axk220167) for CS4485.0W1, Sentence-Builder Project,
* starting October 16, 2026
*/



package Database;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

//The class has AutoCloseable so we can open it from any script and call connection.close() to close it
//or open connections with a try-with-resources to AutoClose
public class DatabaseManager implements AutoCloseable {

    //the connection object associated with all the functions. should not be mutable
    private final Connection conn;

    //the batch size of how many words will accumilate before a DB transaction will be made
    private static final int BATCH_SIZE = 5000;

    //upsert statement for transition and word that either inserts new word or updates on existing word
    private static final String WORD_UPSERT = "INSERT INTO words (word, total_frequency, start_frequency, end_frequency) "
            + "VALUES (?, ?, ?, ?) AS new_data "
            + "ON DUPLICATE KEY UPDATE "
            + "total_frequency = words.total_frequency + new_data.total_frequency, "
            + "start_frequency = words.start_frequency + new_data.start_frequency, "
            + "end_frequency = words.end_frequency + new_data.end_frequency";
    private static final String TRANSITION_UPSERT = "INSERT INTO transitions (word_id, next_word_id, frequency) "
            + "VALUES (?, ?, ?) AS new_data "
            + "ON DUPLICATE KEY UPDATE "
            + "frequency = transitions.frequency + new_data.frequency";

    //connection constructor takes in your url(see below). username and password of your DB connection
    //url takes the form of: "jdbc:mysql://hostname:port/database_name"
    //typically from our schema: "jdbc:mysql://localhost:3306/sentence_builder"
    public  DatabaseManager(String url, String user, String pword) throws SQLException {
        this.conn = DriverManager.getConnection(url, user, pword);
        System.out.println("Successfully opened connection to DB");
    }

    //AutoCloseable's close function overriden to close and inform the close
    @Override
    public void close() throws SQLException {
        if(!conn.isClosed())
            conn.close();
        System.out.println("Successfully closed connection to DB");
    }

    //execute helper method. takes in a query and any number of Object type parameters
    //gets passed query as the prepared statement and loops through any number of passed parameters to attach values to the statements
    //returns the number of rows affected by the passed query or throws an exception
    private int execute(String query, Object... params) throws SQLException {
        try(PreparedStatement ps = conn.prepareStatement(query)){
            for(int i = 0; i < params.length; i++)
                ps.setObject(i+1, params[i]);
            return ps.executeUpdate();
        }

    }

    //use an insert into words table for new words and on the word already existing, update the frequencies as old frequencies + new frequencies
    public int addWord(String word, int totalFrequency, int startFrequency, int endFrequency) throws SQLException{
        //upsert the word
        execute(WORD_UPSERT, word, totalFrequency, startFrequency, endFrequency);

        //check the ID and return the ID or throw an exception if the ID is missing
        OptionalInt id = getWordID(word);
        if(id.isPresent())
            return  id.getAsInt();
        else
            throw new SQLException("Word missing right after insert: "  + word);
    }

    //use insert into transitions table for new word chains and on already existing word update the total frequency
    public void addTransition(int wordId, int nextWordId, int totalOccurrence) throws SQLException {
        execute(TRANSITION_UPSERT, wordId, nextWordId, totalOccurrence);
    }

    //simply add the new file after checking that it is not in the database already
    //returns true on successful adds
    public boolean addImportedFile(String filename, int wordCount) throws SQLException{
        if(hasImportedFile(filename))
            return false;

        String sql = "INSERT INTO imported_files (filename, wordcount) VALUES (?, ?)";

        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, filename);
            ps.setInt(2, wordCount);
            ps.executeUpdate();
        }
        return  true;
    }

    //update a single wordID word to be a different word
    //throws an exception if the new text is already belongs to a different word since each word must be unique in the DB
    //returns true on successful changes false otherwise
    public boolean updateWordText(int wordId, String newWord) throws SQLException{
        OptionalInt existing = getWordID(newWord);

        //another wordid exists with this text
        if(existing.isPresent() && existing.getAsInt() != wordId)
            throw new SQLException("Cannot rename word " + wordId + ": \"" + newWord + "\" already belongs to word " + existing.getAsInt());
        return (execute("UPDATE words SET word = ? WHERE word_id = ?", newWord, wordId)) > 0;
    }

    //update the frequency counts of a specific word
    //returns true if successful
    public boolean updateWordFrequencies(int wordId, int totalFrequency, int startFrequency, int endFrequency) throws SQLException{
        String sql_query = "UPDATE words SET total_frequency = ?, start_frequency = ?, end_frequency = ? "
                + "WHERE word_id = ?";
        return (execute( sql_query,totalFrequency, startFrequency, endFrequency, wordId)) > 0;
    }

    //update the frequency of a specific word, nextword pair
    //return true if successful
    public boolean updateTransitionFrequency(int wordId, int nextWordId, int frequency) throws SQLException{
        return (execute("UPDATE transitions SET frequency = ? WHERE word_id = ? AND next_word_id = ?", frequency, wordId, nextWordId)) > 0;

    }

    //runs a query to check if the DB has an entry for a file with the passed in filename
    public boolean hasImportedFile(String filename) throws SQLException {
        try(PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM imported_files WHERE filename = ?")){
            ps.setString(1, filename);
            try(ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    //run a query to get the id of a passed word
    //used here to check if adding was successful
    public OptionalInt getWordID(String word) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement("SELECT word_id FROM words WHERE word = ?")){
            ps.setString(1, word);
            try(ResultSet rs = ps.executeQuery()){
                if(!rs.next())
                    return OptionalInt.empty();
                else
                    return OptionalInt.of(rs.getInt(1));
            }
        }
    }

    //removes a passed wordID, nextWordID pair
    public boolean removeTransition(int wordId, int nextWordID) throws SQLException{
        return (execute("DELETE FROM transitions WHERE word_id = ? AND next_word_id = ?", wordId, nextWordID)) > 0;
    }

    //remove a file from the imported file table
    public  boolean removeImportedFile(int fileID) throws SQLException{
        return (execute("DELETE FROM imported_files WHERE file_id = ?", fileID)) > 0;
    }

    //Setup for an Atomic operation that will only commit if the action successfully completes and roll back if any exception is thrown
    //this will later be called by the batch adding and remove words
    //interface for a given SQL Action
    private  interface SqlAction{
        void run() throws SQLException;
    }

    //An all or nothing commit
    private void inTransaction(SqlAction action) throws SQLException{
        //save the previous option of auto commits and close it for the upcoming actions
        boolean oldAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try{
            action.run();
            conn.commit();
        } catch (SQLException | RuntimeException e){
            //on any exception undo the changes done to the DB and throw the excption recieverd
            conn.rollback();
            throw e;
        } finally {
            //after trying to do changes (on success or failure) restore original autocommit state
            conn.setAutoCommit(oldAutoCommit);
        }
    }

    //Remove a word from the word table and the word and every transition in the transitions table
    public boolean removeWord(int wordId) throws SQLException{
        //making use of a lambda function to override and implement SqlAction.run
        //use removed to extract the boolean value for if something was removed as lambda cannot assign local variables
        boolean[] removed = new boolean[1];
        //handoff to inTransaction and implement run as lambda
        inTransaction(() -> {
            execute("DELETE FROM transitions WHERE word_id = ? OR next_word_id = ?", wordId, wordId);
            removed[0] = (execute("DELETE FROM words WHERE word_id = ?", wordId)) > 0;
        });
        return removed[0];
    }


    //to batch update the words we need the all or nothing commit + words list + transitions
    //Map<String, long[]> words and Map<String, Map<String, int>> transitions
    //the long array for words holds total, start and end frequencies and transition is a map of maps of word x->word y
    public void addBatch(Map<String, long[]> words, Map<String, Map<String, Integer>> transitions) throws SQLException{

        //use the lambda function in the same way its used in removeWord to overload the SqlAction's run
        inTransaction(() -> {
            batchUpsertWords(words);
            //retrieve the ids of the word you added
            Map<String, Integer> ids = getIds();

            batchUpsertTransition(transitions, ids);
        });
    }

    //take the pased words and set them as the upsert statement's value
    //batch the queries as we go and on reaching our batch size, push them to the DB
    private void batchUpsertWords(Map<String, long[]> words) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement(WORD_UPSERT)){
            int n = 0;
            //get each pair as one Map.Entry and load word, total, start, and end frequency in the prep statement and add it to batch later
            for(Map.Entry<String, long[]> e: words.entrySet()){
                ps.setString(1, e.getKey());
                ps.setLong(2, e.getValue()[0]);
                ps.setLong(3, e.getValue()[1]);
                ps.setLong(4, e.getValue()[2]);

                ps.addBatch();
                //once we reach our specified batch size, execute the query
                if(++n % BATCH_SIZE == 0)
                    ps.executeBatch();
            }
            //account for any leftover words in the remaining batch
            ps.executeBatch();
        }

    }

    //take in teh map of maps and a map of ids and set them to the upsert statement's values
    //batch the queries as we go and on reaching our batch size push them to the DB
    private void batchUpsertTransition(Map<String, Map<String, Integer>> transitions, Map<String, Integer> ids) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement(TRANSITION_UPSERT)){
            int n = 0;
            //for each from word transition get the starting word(key) and the map(chain) of the following words
            for(Map.Entry<String, Map<String, Integer>> fromWord : transitions.entrySet()){
                //safety to avoid null pointers
                int fromId = idOf(ids, fromWord.getKey());
                //for each word that follows the current word get the word's id and the count and set the prep statement
                for(Map.Entry<String, Integer> toWord : fromWord.getValue().entrySet()){
                    ps.setInt(1, fromId);
                    ps.setInt(2, idOf(ids, toWord.getKey()));
                    ps.setInt(3, toWord.getValue());

                    ps.addBatch();
                    //once we reach our batch size, execute the query
                    if(++n % BATCH_SIZE == 0)
                        ps.executeBatch();

                }
            }
            //account for any leftober words in the remaining batch
            ps.executeBatch();
        }
    }

    //look up a word id to prevent nullpointers when unboxing an Integer object
    private int idOf(Map<String, Integer> ids, String word) throws SQLException {
        Integer id = ids.get(word);
        if(id == null)
            throw new SQLException("No word_id found for [" + word + "] - check that it was added to words table already");
        return id;
    }

    //run a query to get all DB's words and ids
    //load and return them as a map for batching to use
    private Map<String, Integer> getIds() throws SQLException{
        Map<String, Integer> ids = new HashMap<>();

        try(Statement st = conn.createStatement()){
            try(ResultSet rs = st.executeQuery("SELECT word_id, word FROM words")){
                while(rs.next())
                    ids.put(rs.getString(2), rs.getInt(1));
            }
        }

        return ids;
    }
}
