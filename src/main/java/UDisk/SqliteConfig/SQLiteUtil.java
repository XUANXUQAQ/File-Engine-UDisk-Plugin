package UDisk.SqliteConfig;

import org.sqlite.SQLiteConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteUtil {
    private static SQLiteConfig sqLiteConfig;
    private static volatile boolean isInitialized = false;
    private static String dbUrl;

    private static void init() {
        sqLiteConfig = new SQLiteConfig();
        sqLiteConfig.setTempStore(SQLiteConfig.TempStore.MEMORY);
        sqLiteConfig.setJournalMode(SQLiteConfig.JournalMode.OFF);
        sqLiteConfig.setPageSize(16384);
        sqLiteConfig.setDefaultCacheSize(50000);
        sqLiteConfig.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
        sqLiteConfig.setLockingMode(SQLiteConfig.LockingMode.NORMAL);
    }

    public static void initConnection(String url) {
        if (!isInitialized) {
            init();
            isInitialized = true;
        }
        dbUrl = url;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, sqLiteConfig.toProperties());
    }

    public static void createAllTables() throws Exception {
        String sql = "CREATE TABLE IF NOT EXISTS list";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("BEGIN;");
            for (int i = 0; i <= 40; i++) {
                String command = sql + i + " " + "(ASCII int, PATH text unique)" + ";";
                stmt.executeUpdate(command);
            }
            stmt.execute("COMMIT;");
        }
    }
}
