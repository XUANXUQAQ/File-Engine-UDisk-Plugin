package UDisk.utils;

import java.io.*;

public class FileUtil {

    public static String getParentPath(String path) {
        File f = new File(path);
        return f.getParentFile().getAbsolutePath();
    }

    public static String getFileName(String path) {
        if (path != null) {
            int index = path.lastIndexOf(File.separator);
            return path.substring(index + 1);
        }
        return "";
    }
}
