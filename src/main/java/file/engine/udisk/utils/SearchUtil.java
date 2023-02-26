package file.engine.udisk.utils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.List;

public class SearchUtil {
    public static void searchFiles(String path) throws IOException, InterruptedException, SQLException {
        Connection connection = SQLiteUtil.getConnection();
        Statement stmt = connection.createStatement();
        try {
            File file = new File(path);
            stmt.execute("BEGIN;");
            queryDir(file, stmt);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            stmt.execute("COMMIT;");
            stmt.close();
            connection.close();
        }
    }

    private static void queryDir(File path, Statement stmt) throws SQLException {
        File[] files = path.listFiles();
        if (null == files || files.length == 0) {
            return;
        }
        List<File> filesList = List.of(files);
        ArrayDeque<File> listRemainDir = new ArrayDeque<>(filesList);
        do {
            File remain = listRemainDir.poll();
            if (remain == null) {
                continue;
            }
            if (remain.isDirectory()) {
                saveToDb(stmt, remain);
                File[] subFiles = remain.listFiles();
                if (subFiles != null) {
                    List<File> subFilesList = List.of(subFiles);
                    listRemainDir.addAll(subFilesList);
                }
            } else {
                saveToDb(stmt, remain);
            }
        } while (!listRemainDir.isEmpty());
    }

    private static void saveToDb(Statement stmt, File eachDir) throws SQLException {
        String name = eachDir.getName();
        int ascii = 0;
        for (int i = 0; i < name.length(); i++) {
            ascii += name.charAt(i);
        }
        int asciiGroup = ascii / 100;
        if (asciiGroup > 40) {
            asciiGroup = 40;
        }
        String sql = "INSERT OR IGNORE INTO list%d VALUES (%d, \"%s\");";
        sql = String.format(sql, asciiGroup, ascii, eachDir.getAbsolutePath());
        stmt.executeUpdate(sql);
    }
}
