package backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


// Oudom Pach OTP220001 10/16/2026, This class is responsible for establishing a connection to the MySQL database. It uses JDBC to connect to the database and provides a method to retrieve the connection object.
public class DatabaseConnector {
    private static final String URL = "jdbc:mysql://localhost:3306/sentence_builder"; //JDBC protocol to connect to local host port 3306
    private static final String USER = "root";
    private static final String PASSWORD = "password";

    //this is a reusable method that can be called so other parts can use it to talk to DB
    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD); //will try open network connection to DB with credentials
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage()); //catch block will be for failed connection
            return null;
        }
    }
}