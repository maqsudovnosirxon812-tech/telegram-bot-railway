package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    private static Connection connection;

    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = Env.get("DB_URL");
            String user = Env.get("DB_USER");
            String pass = Env.get("DB_PASSWORD");
            connection = DriverManager.getConnection(url, user, pass);
        }
        return connection;
    }
}
