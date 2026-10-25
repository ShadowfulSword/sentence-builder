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
import java.util.Collection;
import java.util.Map;


//The class has AutoCloseable so we can open it from any script and call connection.close() to close it
//or open connections with a try-with-resources to AutoClose
public class DatabaseManager implements AutoCloseable {

    //the cion object associated with all the functions. should not be mutable
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
    private static final String TRANSITION_UPSERT = "INSERT INTO transitions (word, next_word, frequency) "
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

    //take in one word and increment the total_frequency by 1
    //take in start and end as booleans to tick each of them up by one
    public void addWord(String word, boolean isStart, boolean isEnd) throws SQLException{
        //upsert the word
        execute(WORD_UPSERT, word, 1, isStart ? 1 : 0, isEnd ? 1 : 0);
    }

    //use insert into transitions table for new word chains and on already existing word update the total frequency
    //records 1 occurance
    public void addTransition(String word, String nextWord) throws SQLException {
        execute(TRANSITION_UPSERT, word, nextWord, 1);
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


    //update a single word to be a different word
    //throws an exception if the new text is already belongs to a different word since each word must be unique in the DB
    //returns true on successful changes false otherwise
    public boolean updateWordText(String oldWord, String newWord) throws SQLException{
        if(!oldWord.equals(newWord) && hasWord(newWord))
            throw new SQLException("Cannot rename word " + oldWord + ": \"" + newWord + "\" already exists");
        return (execute("UPDATE words SET word = ? WHERE word = ?", newWord, oldWord)) > 0;
    }

    //update the frequency counts of a specific word
    //returns true if successful
    public boolean updateWordFrequencies(String word, int totalFrequency, int startFrequency, int endFrequency) throws SQLException{
        String sql_query = "UPDATE words SET total_frequency = ?, start_frequency = ?, end_frequency = ? "
                + "WHERE word = ?";
        return (execute( sql_query,totalFrequency, startFrequency, endFrequency, word)) > 0;
    }

    //update the frequency of a specific word, nextword pair
    //return true if successful
    public boolean updateTransitionFrequency(String word, String nextWord, int frequency) throws SQLException{
        return (execute("UPDATE transitions SET frequency = ? WHERE word = ? AND next_word = ?", frequency, word, nextWord)) > 0;

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

    //run a query to check if a passed word exists in the word table
    public boolean hasWord(String word) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM words where word = ?")){
            ps.setString(1, word);
            try(ResultSet rs = ps.executeQuery()){
                return rs.next();
            }
        }
    }

    //removes a passed word, nextWordpair
    public boolean removeTransition(String word, String nextWord) throws SQLException{
        return (execute("DELETE FROM transitions WHERE word = ? AND next_word = ?", word, nextWord)) > 0;
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
    public boolean removeWord(String word) throws SQLException{
        //making use of a lambda function to override and implement SqlAction.run
        //use removed to extract the boolean value for if something was removed as lambda cannot assign local variables
        boolean[] removed = new boolean[1];
        //handoff to inTransaction and implement run as lambda
        inTransaction(() -> {
            execute("DELETE FROM transitions WHERE word = ? OR next_word = ?", word, word);
            removed[0] = (execute("DELETE FROM words WHERE word = ?", word)) > 0;
        });
        return removed[0];
    }


    //to batch update the words we need the all or nothing commit + words list + transitions
    //Map<String, long[]> words and Map<String, Map<String, int>> transitions
    //the long array for words holds total, start and end frequencies and transition is a map of maps of word x->word y
    public void batchAdd(Collection<WordData> words, Collection<TransitionData> transitions) throws SQLException{

        //use the lambda function in the same way its used in removeWord to overload the SqlAction's run
        inTransaction(() -> {
            batchUpsertWords(words);
            batchUpsertTransition(transitions);
        });
    }

    //pass a class for the data over a map or a list of classes for the batching

    //take the pased words and set them as the upsert statement's value
    //batch the queries as we go and on reaching our batch size, push them to the DB
    private void batchUpsertWords(Collection<WordData> words) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement(WORD_UPSERT)){
            int n = 0;
            //get each pair as one Map.Entry and load word, total, start, and end frequency in the prep statement and add it to batch later
            for(WordData w : words){
                ps.setString(1, w.getWord());
                ps.setLong(2, w.getTotalFrequency());
                ps.setLong(3, w.getStartFrequency());
                ps.setLong(4, w.getEndFrequency());

                ps.addBatch();
                //once we reach our specified batch size, execute the query
                if(++n % BATCH_SIZE == 0)
                    ps.executeBatch();
            }
            //account for any leftover words in the remaining batch
            ps.executeBatch();
        }

    }

    //take in teh map of maps of from word and the map of their to words
    //batch the queries as we go and on reaching our batch size push them to the DB
    private void batchUpsertTransition(Collection<TransitionData> transitions) throws SQLException{
        try(PreparedStatement ps = conn.prepareStatement(TRANSITION_UPSERT)){
            int n = 0;
            for(TransitionData t: transitions){
                ps.setString(1, t.getWord());
                ps.setString(2, t.getWord());
                ps.setInt(3, t.getFrequency());

                ps.addBatch();

                if(++n %  BATCH_SIZE == 0)
                    ps.executeBatch();
            }

            ps.executeBatch();
        }
    }
}
