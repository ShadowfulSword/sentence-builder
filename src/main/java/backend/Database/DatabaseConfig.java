package backend.Database;
import java.sql.SQLException;

public final class DatabaseConfig {
    private DatabaseConfig() {}

    //returns the env variable's val or a default
    private static String env(String name, String fallback){
        String val = System.getenv(name);
        return (val == null || val.isBlank() ? fallback : val);
    }

    //open a new connection using the env files. the caller is responsible for closing it
    //please use try-with-resources
    public static DatabaseManager open() throws SQLException {
        return new DatabaseManager(
                env("SB_DB_URL", "jdbc:mysql://localhost:3306/sentence_builder?rewriteBatchedStatements=true"),
                env("SB_DB_USER", "root"),
                env("SB_DB_PASSWORD", ""));
    }

}
