package UDisk.Search;

import java.io.File;
import java.io.IOException;

public class SearchUDisk {
    public static void searchFiles(String path, String databasePath) throws IOException, InterruptedException {
        File fileSearcher = new File("plugins/Plugin configuration files/UDisk/fileSearcher.exe");
        String absPath = fileSearcher.getAbsolutePath();
        String start = absPath.substring(0, 2);
        String end = "\"" + absPath.substring(2) + "\"";
        File database = new File(databasePath);
        String command = "cmd.exe /c " + start + end + " \"" + path + "\"" + " \"" + 10 + "\" " + "\"" + "placeholder" + "\" " + "\"" + database.getAbsolutePath() + "\" " + "\"" + "0" + "\"";
        Process p = Runtime.getRuntime().exec(command, null, new File("user"));
        p.waitFor();
    }
}
