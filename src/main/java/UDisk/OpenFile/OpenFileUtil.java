package UDisk.OpenFile;

import javax.swing.*;
import java.awt.*;
import java.io.*;

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
                    e.printStackTrace();
                }
            }
        }
    }

    public static void openWithoutAdmin(String path) {
        File file = new File(path);
        String pathLower = path.toLowerCase();
        if (file.exists()) {
            try {
                if (pathLower.endsWith(".url")) {
                    Desktop desktop;
                    if (Desktop.isDesktopSupported()) {
                        desktop = Desktop.getDesktop();
                        desktop.open(new File(path));
                    }
                } else if (pathLower.endsWith(".lnk")) {
                    Runtime.getRuntime().exec("explorer.exe \"" + path + "\"");
                } else {
                    if (file.isFile()) {
                        String command;
                        if (pathLower.endsWith(".cmd") || pathLower.endsWith(".bat")) {
                            command = "start / k " + path.substring(0, 2) + "\"" + path.substring(2) + "\"";
                        } else {
                            command = "start " + path.substring(0, 2) + "\"" + path.substring(2) + "\"";
                        }
                        String vbsFilePath = generateBatAndVbsFile(command, System.getProperty("java.io.tmpdir"), getParentPath(path));
                        Runtime.getRuntime().exec("explorer.exe " + vbsFilePath.substring(0, 2) + "\"" + vbsFilePath.substring(2) + "\"");
                    } else {
                        Runtime.getRuntime().exec("explorer.exe \"" + path + "\"");
                    }
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

    private static String getParentPath(String path) {
        File f = new File(path);
        return f.getParent();
    }

    /**
     * 在windows的temp目录中生成bat以及用于隐藏bat的vbs脚本
     * @param command 要运行的cmd命令
     * @param filePath 文件位置（必须传入文件夹）
     * @param workingDir 应用打开后的工作目录
     * @return vbs的路径
     */
    private static String generateBatAndVbsFile(String command, String filePath, String workingDir) {
        char disk = workingDir.charAt(0);
        String start = workingDir.substring(0,2);
        String end = workingDir.substring(2);
        String batFilePath = filePath + "openBat_File_Engine.bat";
        String vbsFilePath = filePath + "openVbs_File_Engine.vbs";
        try (BufferedWriter batW = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(batFilePath), System.getProperty("sun.jnu.encoding")));
             BufferedWriter vbsW = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(vbsFilePath), System.getProperty("sun.jnu.encoding")))) {
            //生成bat
            batW.write(disk + ":");
            batW.newLine();
            batW.write("cd " + start + "\"" + end + "\"");
            batW.newLine();
            batW.write(command);
            //生成vbs
            vbsW.write("set ws=createobject(\"wscript.shell\")");
            vbsW.newLine();
            vbsW.write("ws.run \"" + batFilePath + "\", 0");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return vbsFilePath;
    }
}
