package Database;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

//The class has AutoCloseable so we can open it from any script and call connection.close() to close it
//or open connections with a try-with-resources to AutoClose
public class DatabaseManager implements AutoCloseable {

    //the connection object assoicaiated with all the functions. should not be mutable
    private final Connection conn;

    //upsert statement for transition and word that either inserts new word or updates on existing word
    private final String WORD_UPSERT = "INSERT INTO words (word, total_frequency, start_frequency, end_frequency) "
            + "VALUES (?, ?, ?, ?) AS new_data "
            + "ON DUPLICATE KEY UPDATE "
            + "total_frequency = total_frequency + new_data.total_frequency, "
            + "start_frequency = start_frequency + new_data.start_frequency, "
            + "end_frequency = end_frequency + new_data.end_frequency";
    private final String TRANSITION_UPSERT = "INSERT INTO transitions (word_id, next_word_id, frequency) "
            + "VALUES (?, ?, ?) AS new_data "
            + "ON DUPLICATE KEY UPDATE "
            + "frequency = frequency + new_data.frequency";

    //connection constructor takes in your url(see below). username and password of your DB connection
    //url takes the form of: "jdbc:mysql://hostname:port/database_name"
    //typically from our schema: "jdbc:mysql://localhost:3306/sentence_builder"
    public  DatabaseManager(String url, String user, String pword) throws SQLException {
        this.conn = DriverManager.getConnection(url, user, pword);
        System.out.println("Successfully opened connection to DB");
    }

    //AutoCloseable's close function overriden to clsoe and inform the close
    @Override
    public void close() throws SQLException {
        if(!conn.isClosed())
            conn.close();
        System.out.println("Successfully closed connection to DB");
    }

    //execute helper method. takes in a query and any number of Object type parameters
    //gets passed query as the prepared statement and loops through any number of passed parameters to attach values to the statements
    private void execute(String query, Object... params) throws SQLException {

        try(PreparedStatement ps = conn.prepareStatement(query)){
            for(int i = 0; i < params.length; i++)
                ps.setObject(i+1, params[i]);
            ps.executeUpdate();
        }

    }

    //use an insert into words table for new words and on the word already existing, update the frequencies as old frequencies + new frequencies
    public void add_word(String  word, int total_frequency, int start_frequency, int end_frequency) throws SQLException{
        execute(WORD_UPSERT, word, total_frequency, start_frequency, end_frequency);
    }

    //use insert into transitions table for new word chains and on already existing word update the total frequncy
    public void add_transition(int word_id, int next_word_id, int total_occurance) throws SQLException {
        execute(TRANSITION_UPSERT, word_id, next_word_id, total_occurance);
    }

    //simply add the new file, we don't keep track of the same file trying to be uploaded twice
    public void addImportedFile(String filename, int wordCount) throws SQLException{
        String sql = "INSERT INTO imported_files (filename, wordcount) VALUES (?, ?)";

        try(PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, filename);
            ps.setInt(2, wordCount);
            ps.executeUpdate();
        }
    }




}