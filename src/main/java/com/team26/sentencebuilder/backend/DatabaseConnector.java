// Oudom Pach OTP220001 10/16/2026, This class is responsible for establishing a connection to the MySQL database. It uses JDBC to connect to the database and provides a method to retrieve the connection object.
package com.team26.sentencebuilder.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnector {
    private static final String URL = "jdbc:mysql://localhost:3306/sentence_builder";
    private static final String USER = "root";
    private static final String PASSWORD = "password";

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
            return null;
        }
    }
}