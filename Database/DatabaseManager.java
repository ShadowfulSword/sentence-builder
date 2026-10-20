package Database;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

//The class has AutoCloseable so we can open it from any script and call connection.close() to close it
//or open connections with a try-with-resources to AutoClose
public class DatabaseManager implements AutoCloseable {

    //the connection object assoicaiated with all the functions. should not be mutable
    private final Connection conn;

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
        conn.close();
        System.out.println("Successfully closed connection to DB");
    }

    //use an insert into words table for new words and on the word already existing, update the frequencies as old frequencies + new frequencies
    public void add_word(String  word, int total_frequency, int start_frequency, String end_frequency) throws SQLException{

    }


    public void add_transition(String word, int next_word_id, int total_occurance){

    }
}