package UDisk.OpenFile;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class OpenFileUtil {
    public static void openWithAdmin(String path) {
        File name = new File(path);
        if (name.exists()) {
            try {
                String command = name.getAbsolutePath();
                String start = "cmd.exe /c start " + command.substring(0, 2);
                String end = "\"" + command.substring(2) + "\"";
                Runtime.getRuntime().exec(start + end, null, name.getParentFile());
            } catch (IOException e) {
                //打开上级文件夹
                try {
                    Runtime.getRuntime().exec("explorer.exe /select, \"" + name.getAbsolutePath() + "\"");
                } catch (IOException e1) {
                    JOptionPane.showMessageDialog(null, "Execute failed");
                }
            }
        }
    }

    public static void openWithoutAdmin(String path) {
        File f = new File(path);
        if (f.exists()) {
            try {
                if (path.toLowerCase().endsWith(".lnk")) {
                    String command = "explorer.exe " + "\"" + path + "\"";
                    Runtime.getRuntime().exec(command);
                } else if (path.toLowerCase().endsWith(".url")) {
                    Desktop desktop;
                    if (Desktop.isDesktopSupported()) {
                        desktop = Desktop.getDesktop();
                        desktop.open(new File(path));
                    }
                } else {
                    //创建快捷方式到临时文件夹，打开后删除
                    File tmp = new File("tmp");
                    File open = new File("tmp/open");
                    createShortCut(path, open.getAbsolutePath());
                    Runtime.getRuntime().exec("explorer.exe " + "\"" + tmp.getAbsolutePath() + File.separator + "open.lnk" + "\"");
                }
            } catch (Exception e) {
                //打开上级文件夹
                try {
                    Runtime.getRuntime().exec("explorer.exe /select, \"" + path + "\"");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    private static void createShortCut(String fileOrFolderPath, String writeShortCutPath) throws Exception {
        File shortcutGen = new File("user/shortcutGenerator.vbs");
        String shortcutGenPath = shortcutGen.getAbsolutePath();
        String start = "cmd.exe /c start " + shortcutGenPath.substring(0, 2);
        String end = "\"" + shortcutGenPath.substring(2) + "\"";
        String commandToGenLnk = start + end + " /target:" + "\"" + fileOrFolderPath + "\"" + " " + "/shortcut:" + "\"" + writeShortCutPath + "\"" + " /workingdir:" + "\"" + fileOrFolderPath.substring(0, fileOrFolderPath.lastIndexOf(File.separator)) + "\"";
        Process p = Runtime.getRuntime().exec("cmd.exe /c " + commandToGenLnk);
        while (p.isAlive()) {
            Thread.sleep(1);
        }
    }
}
