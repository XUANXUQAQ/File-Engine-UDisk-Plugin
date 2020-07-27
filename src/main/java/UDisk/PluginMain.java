package UDisk;

import UDisk.DllInterface.GetAscII;
import UDisk.SqliteConfig.SQLiteUtil;
import UDisk.GetIcon.GetIconUtil;
import UDisk.VersionCheck.VersionCheckUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import static UDisk.OpenFile.OpenFileUtil.openWithAdmin;
import static UDisk.OpenFile.OpenFileUtil.openWithoutAdmin;
import static UDisk.Search.SearchUDisk.searchFiles;

public class PluginMain extends Plugin {
    private final String configurationPath = "plugins/Plugin configuration files/UDisk";
    private final String databaseRelativePath = "plugins/Plugin configuration files/UDisk/data.db";
    private final String settingsRelativePath = "plugins/Plugin configuration files/UDisk/settings.json";
    private final String[] arr = new String[] {"A","B","C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
    private boolean isNotExit = true;
    private long startTime;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile String[] searchCase;
    private volatile String searchText;
    private static volatile String[] keywords;
    private final Pattern colon = Pattern.compile(":");
    private final Pattern semicolon = Pattern.compile(";");
    private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isRunAsAdminPressed = false;
    private volatile boolean isCopyPathPressed = false;
    private volatile boolean isIndexMode = false;
    private volatile boolean isOpenLastFolderPressed = false;
    private int pluginIconSideLength = 0;
    private final Border border = BorderFactory.createLineBorder(new Color(73, 162, 255, 255));
    private Color pluginLabelColor;
    private Color pluginBackgroundColor;
    private volatile String text;
    private volatile int openLastFolderKeyCode;
    private volatile int runAsAdminKeyCode;
    private volatile int copyPathKeyCode;
    private boolean timer = false;
    private final HashMap<String, Boolean> map = new HashMap<>();
    private final HashMap<String, PreparedStatement> sqlCache = new HashMap<>();

    private void releaseAllSqlCache() {
        for (String each : sqlCache.keySet()) {
            try {
                sqlCache.get(each).close();
            } catch (SQLException ignored) {

            }
        }
    }

    private void initDll() {
        try {
            Class.forName("UDisk.DllInterface.GetAscII");
        }catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private String getFileName(String path) {
        int index = path.lastIndexOf(File.separator);
        return path.substring(index + 1);
    }

    private boolean isFile(String path) {
        File file = new File(path);
        return file.isFile();
    }

    private boolean isDirectory(String text) {
        File file = new File(text);
        return file.isDirectory();
    }

    private boolean isMatched(String name, boolean isIgnoreCase) {
        for (String each : keywords) {
            if (isIgnoreCase) {
                if (!name.toLowerCase().contains(each.toLowerCase())) {
                    return false;
                }
            } else {
                if (!name.contains(each)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void saveSettings() {
        JSONObject json = new JSONObject();
        json.put("labelColor", pluginLabelColor.getRGB());
        json.put("backgroundColor", pluginBackgroundColor.getRGB());
        json.put("openLastFolderKeyCode", openLastFolderKeyCode);
        json.put("runAsAdminKeyCode", runAsAdminKeyCode);
        json.put("copyPathKeyCode", copyPathKeyCode);
        try (BufferedWriter buffW = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(settingsRelativePath), StandardCharsets.UTF_8))) {
            String format = JSON.toJSONString(json, SerializerFeature.PrettyFormat, SerializerFeature.WriteMapNullValue, SerializerFeature.WriteDateUseDateFormat);
            buffW.write(format);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initAllSettings() {
        String line;
        StringBuilder strb = new StringBuilder();
        File settings = new File(settingsRelativePath);
        try (BufferedReader buffr = new BufferedReader(new InputStreamReader(new FileInputStream(settings), StandardCharsets.UTF_8))) {
            while ((line = buffr.readLine()) != null) {
                strb.append(line);
            }
            JSONObject settingsInJson = JSON.parseObject(strb.toString());
            if (settingsInJson.containsKey("labelColor")) {
                pluginLabelColor = new Color(settingsInJson.getInteger("labelColor"));
            } else {
                pluginLabelColor = new Color(0xFF9868);
            }
            if (settingsInJson.containsKey("backgroundColor")) {
                pluginBackgroundColor = new Color(settingsInJson.getInteger("backgroundColor"));
            } else {
                pluginBackgroundColor = new Color(0xffffff);
            }
            if (settingsInJson.containsKey("openLastFolderKeyCode")) {
                openLastFolderKeyCode = settingsInJson.getInteger("openLastFolderKeyCode");
            } else {
                openLastFolderKeyCode = 17;
            }
            if (settingsInJson.containsKey("runAsAdminKeyCode")) {
                runAsAdminKeyCode = settingsInJson.getInteger("runAsAdminKeyCode");
            } else {
                runAsAdminKeyCode = 16;
            }
            if (settingsInJson.containsKey("copyPathKeyCode")) {
                copyPathKeyCode = settingsInJson.getInteger("copyPathKeyCode");
            } else {
                copyPathKeyCode = 18;
            }
        } catch (NullPointerException | IOException e) {
            pluginLabelColor = new Color(0xFF9868);
            pluginBackgroundColor = new Color(0xffffff);
            openLastFolderKeyCode = 17;
            runAsAdminKeyCode = 16;
            copyPathKeyCode = 18;
        }
    }

    private boolean check(String path) {
        String name = getFileName(path);
        if (searchCase == null || searchCase.length == 0) {
            return isMatched(name, true);
        } else {
            if (isMatched(name, true)) {
                for (String eachCase : searchCase) {
                    switch (eachCase) {
                        case "f":
                            if (!isFile(path)) {
                                return false;
                            }
                            break;
                        case "d":
                            if (!isDirectory(path)) {
                                return false;
                            }
                            break;
                        case "full":
                            if (!name.equalsIgnoreCase(searchText)) {
                                return false;
                            }
                            break;
                        case "case":
                            if (!isMatched(name, false)) {
                                return false;
                            }
                        default:
                            break;
                    }
                }
                //所有规则均已匹配
                return true;
            }
        }
        return false;
    }

    private boolean isExist(String path) {
        File f = new File(path);
        return f.exists();
    }

    private void checkIsMatchedAndAddToList(String path) {
        if (check(path)) {
            if (isExist(path)) {
                addToResultQueue(path);
            }
        }
    }

    private void addResult(long time, String column) throws SQLException {
        //为label添加结果
        ResultSet resultSet;
        String each;
        String pSql = "SELECT PATH FROM " + column + ";";
        PreparedStatement pStmt;
        if ((pStmt = sqlCache.get(pSql)) == null) {
            pStmt = SQLiteUtil.getConnection().prepareStatement(pSql);
            sqlCache.put(pSql, pStmt);
        }

        resultSet = pStmt.executeQuery();
        while (resultSet.next()) {
            each = resultSet.getString("PATH");
            checkIsMatchedAndAddToList(each);
            //用户重新输入了信息
            if (startTime > time) {
                break;
            }
        }
        resultSet.close();
    }

    private int getAscIISum(String path) {
        path = path.toUpperCase();
        if (path.contains(";")) {
            path = path.replace(";", "");
        }
        return GetAscII.INSTANCE.getAscII(path);
    }

    private String getParentPath(String path) {
        File f = new File(path);
        return f.getParent();
    }

    // 初始化磁盘状态，存在true， 否则false
    private void initDiskStatus() {
        File file ;
        for(String str : arr) {
            file = new File(str + ":\\");
            map.put(str, file.exists());
        }
    }

    private String checkUDisk() {
        File file ;
        String disk = null;
        for(;;) {
            for(String str : arr) {
                file = new File(str + ":\\");
                // 如果磁盘现在存在，并且以前不存在
                // 则表示刚插上U盘，返回
                if(file.exists() && !map.get(str)) {
                    disk = str;
                }

                if(file.exists() != map.get(str)) {
                    map.put(str, file.exists());
                }
            }

            if (disk != null) {
                return disk;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {

            }
        }
    }

    private void initThreadPool() {
        threadPool.execute(() -> {
            String UDisk;
            while (isNotExit) {
                UDisk = checkUDisk();
                displayMessage("Info", "Input " + "\"" + " >udisk >" + UDisk + "\"" + " to initialize index");
            }
        });

        threadPool.execute(() -> {
            try {
                String column;
                while (isNotExit) {
                    try {
                        while ((column = commandQueue.poll()) != null) {
                            addResult(System.currentTimeMillis(), column);
                        }
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException ignored) {

            }
        });

        threadPool.execute(() -> { //添加sql命令线程
            try {
                long endTime;
                String command;
                while (isNotExit) {
                    endTime = System.currentTimeMillis();
                    if ((endTime - startTime > 500) && (timer) && !isIndexMode) {
                        timer = false;
                        String name = getFileName(searchText.toUpperCase());
                        int ascII = getAscIISum(name);
                        int asciiGroup = ascII / 100;

                        switch (asciiGroup) {
                            case 0:
                                for (int i = 0; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 1:
                                for (int i = 1; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 2:
                                for (int i = 2; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 3:
                                for (int i = 3; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 4:
                                for (int i = 4; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 5:
                                for (int i = 5; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 6:
                                for (int i = 6; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 7:
                                for (int i = 7; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 8:
                                for (int i = 8; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 9:
                                for (int i = 9; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 10:
                                for (int i = 10; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 11:
                                for (int i = 11; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 12:
                                for (int i = 12; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 13:
                                for (int i = 13; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 14:
                                for (int i = 14; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 15:
                                for (int i = 15; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 16:
                                for (int i = 16; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 17:
                                for (int i = 17; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 18:
                                for (int i = 18; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 19:
                                for (int i = 19; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 20:
                                for (int i = 20; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 21:
                                for (int i = 21; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 22:
                                for (int i = 22; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 23:
                                for (int i = 23; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 24:
                                for (int i = 24; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;

                            case 25:
                                for (int i = 25; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 26:
                                for (int i = 26; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 27:
                                for (int i = 27; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 28:
                                for (int i = 28; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 29:
                                for (int i = 29; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 30:
                                for (int i = 30; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 31:
                                for (int i = 31; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 32:
                                for (int i = 32; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 33:
                                for (int i = 33; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 34:
                                for (int i = 34; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 35:
                                for (int i = 35; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 36:
                                for (int i = 36; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 37:
                                for (int i = 37; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 38:
                                for (int i = 38; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 39:
                                for (int i = 39; i < 40; i++) {
                                    command = "list" + i;
                                    commandQueue.add(command);
                                }
                                break;
                            case 40:
                                command = "list40";
                                commandQueue.add(command);
                                break;
                            default:
                                break;
                        }
                    } else if ((endTime - startTime > 500) && (timer) && isIndexMode) {
                        timer = false;
                        isIndexMode = false;
                        try {
                            if (text != null) {
                                String searchPath = text.charAt(1) + ":";
                                if (!":".equals(searchPath)) {
                                    try {
                                        if (new File(searchPath + "\\").exists()) {
                                            searchFiles(searchPath, databaseRelativePath);
                                            displayMessage("Info", "Search Done");
                                        }
                                    } catch (IOException | InterruptedException e) {
                                        if (!(e instanceof InterruptedException)) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                        } catch (StringIndexOutOfBoundsException ignored) {
                        }
                    }
                    Thread.sleep(50);
                }
            }catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    e.printStackTrace();
                }
            }
        });
    }

    //Do Not Remove
    public String[] getMessage() {
        return _getMessage();
    }

    //Do Not Remove
    public String pollFromResultQueue() {
        return _pollFromResultQueue();
    }

    //Do Not Remove
    public int getApiVersion() {
        return _getApiVersion();
    }

    @Override
    public void textChanged(String _text) {
        if (!_text.isEmpty()) {
            if (_text.contains(">")) {
                text = _text.trim();
                isIndexMode = true;
            } else {
                String[] strings;
                int length;
                strings = colon.split(_text);
                length = strings.length;
                if (length == 2) {
                    searchCase = semicolon.split(strings[1].toLowerCase());
                    searchText = strings[0];
                } else {
                    searchText = strings[0];
                    searchCase = null;
                }
                keywords = semicolon.split(searchText);
                isIndexMode = false;
            }
            commandQueue.clear();
            timer = true;
            startTime = System.currentTimeMillis();
        }
    }

    @Override
    public void loadPlugin() {
        try {
            System.out.println("Loading plugin UDisk");
            File pluginFolder = new File(configurationPath);
            if (!pluginFolder.exists()) {
                pluginFolder.mkdirs();
            }
            File settings = new File(settingsRelativePath);
            if (!settings.exists()) {
                settings.createNewFile();
            }
            initAllSettings();
            saveSettings();

            File database = new File(databaseRelativePath);
            SQLiteUtil.initConnection("jdbc:sqlite:" + database.getAbsolutePath());
            SQLiteUtil.createAllTables();

            initDll();
            initDiskStatus();
            initThreadPool();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unloadPlugin() {
        try {
            System.out.println("Unloading plugin UDisk");
            isNotExit = false;
            threadPool.shutdownNow();
            releaseAllSqlCache();
            SQLiteUtil.clearAllTables();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void keyReleased(KeyEvent e, String result) {
        int key = e.getKeyCode();
        if (openLastFolderKeyCode == key) {
            //复位按键状态
            isOpenLastFolderPressed = false;
        } else if (runAsAdminKeyCode == key) {
            isRunAsAdminPressed = false;
        } else if (copyPathKeyCode == key) {
            isCopyPathPressed = false;
        }
    }

    @Override
    public void keyPressed(KeyEvent e, String result) {
        int key = e.getKeyCode();
        if (10 == key) {
            if (isOpenLastFolderPressed) {
                //打开上级文件夹
                File open = new File(result);
                try {
                    Runtime.getRuntime().exec("explorer.exe /select, \"" + open.getAbsolutePath() + "\"");
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else if (isRunAsAdminPressed) {
                openWithAdmin(result);
            } else if (isCopyPathPressed) {
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable trans = new StringSelection(result);
                clipboard.setContents(trans, null);
            } else {
                if (result.endsWith(".bat") || result.endsWith(".cmd")) {
                    openWithAdmin(result);
                } else {
                    openWithoutAdmin(result);
                }
            }
        }else if (openLastFolderKeyCode == key) {
            //打开上级文件夹热键被点击
            isOpenLastFolderPressed = true;
        } else if (runAsAdminKeyCode == key) {
            //以管理员方式运行热键被点击
            isRunAsAdminPressed = true;
        } else if (copyPathKeyCode == key) {
            isCopyPathPressed = true;
        }
    }

    @Override
    public void keyTyped(KeyEvent e, String result) {

    }

    @Override
    public void mousePressed(MouseEvent e, String result) {
        if (isOpenLastFolderPressed) {
            //打开上级文件夹
            File open = new File(result);
            try {
                Runtime.getRuntime().exec("explorer.exe /select, \"" + open.getAbsolutePath() + "\"");
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } else if (isRunAsAdminPressed) {
            openWithAdmin(result);
        } else if (isCopyPathPressed) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable trans = new StringSelection(result);
            clipboard.setContents(trans, null);
        } else {
            if (result.endsWith(".bat") || result.endsWith(".cmd")) {
                openWithAdmin(result);
            } else {
                openWithoutAdmin(result);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e, String result) {

    }

    @Override
    public ImageIcon getPluginIcon() {
        return new ImageIcon(PluginMain.class.getResource("/icon.png"));
    }

    @Override
    public String getOfficialSite() {
        return "https://github.com/XUANXUQAQ/File-Engine-UDisk-Plugin";
    }


    @Override
    public String getVersion() {
        return VersionCheckUtil._getPluginVersion();
    }

    @Override
    public String getDescription() {
        return "中文说明：\n" +
                "\n" +
                "1.将U盘插入计算机时，\n" +
                "\t您会看到这样的提示\n" +
                "\t---->键入“> udisk>驱动器号”来索引U盘\n" +
                "\t只需按照提示输入，您将收到另一个提示\n" +
                "\t---->搜索完成。\n" +
                "\n" +
                "2.搜索完成后。您可以输入“> udisk test”来搜索名称包含“ test”的文件。\n" +
                "\t示例1：“>“ udisk测试” --->包含“ test”（“ TEST”“ Test”“ TEst” ...）的文件或目录。\n" +
                "\t示例2：“> udisk test1; test2” --->包含“ test1（TEST1）”和“ test2（TEST2）”的文件或目录\n" +
                "\t您也可以使用一些过滤器，例如“：f（file）”“：d（directory）”“：full”“：case”。不同的过滤器应以分号分隔。\n" +
                "\t示例1：“> udisk test：f” ---->仅包含“ test”（“ TEST”“ Test”“ TEst” ...）的文件。\n" +
                "\t示例2：“> udisk test：d” ---->仅包含“ test”（“ TEST”“ Test”“ TEst” ...）的目录。\n" +
                "\t示例3：“> udisk test：full” --->名称为“ test”（“ TEST”“ Test”“ TEst” ...）的文件或目录。\n" +
                "\t示例4：“> udisk test：case” --->包含“ test”的文件或目录。\n" +
                "\t示例5：“> udisk test：f; full” --->仅名称为“ test”（“ TEST”“ Test”“ TEst” ...）的文件。\n" +
                "\t示例6：“> udisk test：d; case; full” --->仅名称为“ test”的目录。\n" +
                "\n" +
                "English Instuction:\n" +
                "\n" +
                "1. When you plug the U disk into the computer,\n" +
                "\tYou will see a tip like this\n" +
                "\t----> Type \">udisk> drive letter\" to index the U disk\n" +
                "\tJust input what the tip says, and you will receive another tip like this\n" +
                "\t----> Search Done.\n" +
                "\n" +
                "2. When the search has done. You can input \">udisk test\" to search files whose name includeing \"test\".\n" +
                "\tExample 1 : \">udisk test\" ---> files or dirs that including \"test\"(\"TEST\" \"Test\" \"TEst\"...).\n" +
                "\tExample 2 : \">udisk test1;test2\" ---> files or dirs that including \"test1(TEST1)\" AND \"test2(TEST2)\"\n" +
                "\tYou can also use some filters like \":f(file)\" \":d(directory)\" \":full\" \":case\".Different filters should be separated by semicolons.\n" +
                "\tExample 1 : \">udisk test:f\" ----> Only files that including \"test\"(\"TEST\" \"Test\" \"TEst\"...).\n" +
                "\tExample 2 : \">udisk test:d\" ----> Only directories that including \"test\"(\"TEST\" \"Test\" \"TEst\"...).\n" +
                "\tExample 3 : \">udisk test:full\" ---> files or dirs whose name is \"test\"(\"TEST\" \"Test\" \"TEst\"...).\n" +
                "\tExample 4 : \">udisk test:case\" ---> files or dirs that including \"test\".\n" +
                "\tExample 5 : \">udisk test:f;full\" ---> Only files whose name is \"test\"(\"TEST\" \"Test\" \"TEst\"...).\n" +
                "\tExample 6 : \">udisk test:d;case;full\" ---> Only dirs whose name is \"test\".";
    }

    @Override
    public boolean isLatest() {
        return VersionCheckUtil._isLatest();
    }

    @Override
    public String getUpdateURL() {
        return VersionCheckUtil._getUpdateURL();
    }

    @Override
    public void showResultOnLabel(String result, JLabel label, boolean isChosen) {
        if (pluginIconSideLength == 0) {
            pluginIconSideLength = label.getHeight() / 3;
        }
        String name = getFileName(result);
        ImageIcon icon = GetIconUtil.getBigIcon(result, pluginIconSideLength, pluginIconSideLength);
        label.setIcon(icon);
        label.setBorder(border);
        label.setText("<html><body>" + name + "<br><font size=\"-1\">" + ">>" + getParentPath(result) + "</body></html>");
        if (isChosen) {
            label.setBackground(pluginLabelColor);
        } else {
            label.setBackground(pluginBackgroundColor);
        }
    }

    @Override
    public String getAuthor() {
        return "XUANXU";
    }
}
