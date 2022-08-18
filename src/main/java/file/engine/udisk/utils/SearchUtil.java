package file.engine.udisk.utils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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

    private static void queryDir(File file, Statement stmt) throws SQLException {
        if (!file.exists()) {
            return;
        }
        File[] content = file.listFiles();//取得当前目录下所有文件和文件夹
        if (content == null || content.length == 0) {
            return;
        }
        for (File temp : content) {
            if (temp.isDirectory()) {//判断是否是目录
                queryDir(temp, stmt);//递归调用，删除目录里的内容
            }
            String name = temp.getName();
            int ascii = 0;
            for (int i = 0; i < name.length(); i++) {
                ascii += name.charAt(i);
            }
            int asciiGroup = ascii / 100;
            if (asciiGroup > 40) {
                asciiGroup = 40;
            }
            String sql = "INSERT OR IGNORE INTO list%d VALUES (%d, \"%s\");";
            sql = String.format(sql, asciiGroup, ascii, temp.getAbsolutePath());
            stmt.executeUpdate(sql);
        }
    }
}
