package core;

import java.sql.Connection;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariDataSource;

public class SQL {

    public static boolean is_connected = false;
    private static SQL instance = null;
    private HikariDataSource dataSource;
    private final String url;
    private final String user;
    private final String pass;

    public SQL() {
        url = "jdbc:mysql://" + Manager.gI().mysql_host + "/" + Manager.gI().mysql_database
                + "?autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8";
        user = Manager.gI().mysql_user;
        pass = Manager.gI().mysql_pass;
        System.out.println("OPEN DataBase connect");
        HikariDataSource config = new HikariDataSource();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        //
        config.setPoolName("HSO_pool");
        config.setMinimumIdle(10);
        config.setMaximumPoolSize(150);
        //
//		config.setConnectionTimeout(3_000L);
//		config.setLeakDetectionThreshold(10_000L);
//		config.setMaxLifetime(1000L);
        //
//		config.addDataSourceProperty("cachePrepStmts", "true");
//		config.addDataSourceProperty("prepStmtCacheSize", "250");
//		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setAutoCommit(false);
        //
        dataSource = new HikariDataSource(config);
        SQL.is_connected = true;
    }

    public static SQL gI() {
        if (instance == null) {
            instance = new SQL();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (SQL.is_connected) {
            if (dataSource != null) {
                // dataSource.close();
                System.out.println("CLOSE DataBase connect");
            }
            SQL.is_connected = false;
        }
    }
}
