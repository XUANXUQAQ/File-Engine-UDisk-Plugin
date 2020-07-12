package UDisk;

import UDisk.DllInterface.GetAscII;
import UDisk.SqliteConfig.SQLiteUtil;
import UDisk.GetIcon.GetIconUtil;
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
    private int iconSideLength = 0;
    private final Border border = BorderFactory.createLineBorder(new Color(73, 162, 255, 255));
    private Color labelColor;
    private Color backgroundColor;
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
        json.put("labelColor", labelColor.getRGB());
        json.put("backgroundColor", backgroundColor.getRGB());
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
                labelColor = new Color(settingsInJson.getInteger("labelColor"));
            } else {
                labelColor = new Color(0xFF9868);
            }
            if (settingsInJson.containsKey("backgroundColor")) {
                backgroundColor = new Color(settingsInJson.getInteger("backgroundColor"));
            } else {
                backgroundColor = new Color(0xffffff);
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
            labelColor = new Color(0xFF9868);
            backgroundColor = new Color(0xffffff);
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
                        }
                    } else if ((endTime - startTime > 500) && (timer) && isIndexMode) {
                        timer = false;
                        isIndexMode = false;
                        try {
                            if (text != null) {
                                String searchPath = text.charAt(1) + ":";
                                if (!searchPath.equals(":")) {
                                    try {
                                        searchFiles(searchPath, databaseRelativePath);
                                        displayMessage("Info", "Search Done");
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
        return null;
    }


    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "中文说明:\n" +
                "\n" +
                "一个插件使File-Engine支持索引U盘.\n" +
                "使用说明：\n" +
                "        1.在搜索框中输入     “>udisk >index:（盘符）”    以索引对应盘中的所有文件.\n" +
                "\n" +
                "示例：索引G盘中的所有文件。 \n" +
                "--------> \">udisk >index:G\"\n" +
                "\n" +
                "        2.之后，在搜索框中输入 \">udisk (关键字)\"来搜索含有该关键字的文件。\n" +
                "\n" +
                "示例：搜索含有test的所有文件. \n" +
                "-------->\">udisk test\"\n" +
                "\n" +
                "        3.规则匹配\n" +
                "与File-Engine使用相同的规则，在关键字后添加 \":d\"(\":f\")来只搜索文件夹（文件）,添加 \":full\"全字匹配，添加\":case\"匹配大小写.\n" +
                "不同的规则用分号隔开。\n" +
                "\n" +
                "示例：\n" +
                "搜索含有test，全字匹配，并只输出文件。\n" +
                "-------->\">udisk test:full;f\"\n" +
                "搜索含有test，只输出文件夹\n" +
                "-------->\">udisk test:d\"\n" +
                "\n" +
                "English Instruction:\n" +
                "\n" +
                "A plugin enables File-Engine to support indexing U disks.\n" +
                "Instructions for use:\n" +
                "         1. Type \">udisk >index: (drive letter)\" in the search box to index all files in the corresponding disk.\n" +
                "\n" +
                "Example: Index all files in the G drive.\n" +
                "--------> \">udisk >index:G\"\n" +
                "\n" +
                "         2. After that, enter \">udisk (keyword)\" in the search box to search for the file containing the keyword.\n" +
                "\n" +
                "Example: Search all files containing test.\n" +
                "-------->\">udisk test\"\n" +
                "\n" +
                "         3. Rule matching\n" +
                "Use the same rules as File-Engine, add \":d\" (\":f\") after the keyword to search only the folder (file), add \":full\" to match the whole word, add \":case\" to match the case .\n" +
                "Different rules are separated by semicolons.\n" +
                "\n" +
                "Examples:\n" +
                "The search contains test, matches all words, and only outputs files.\n" +
                "-------->\">udisk test:full;f\"\n" +
                "Search contains test, only output folder\n" +
                "-------->\">udisk test:d\"";
    }

    @Override
    public boolean isLatest() {
        return true;
    }

    @Override
    public String getUpdateURL() {
        return null;
    }

    @Override
    public void showResultOnLabel(String result, JLabel label, boolean isChosen) {
        if (iconSideLength == 0) {
            iconSideLength = label.getHeight() / 3;
        }
        String name = getFileName(result);
        ImageIcon icon = GetIconUtil.getBigIcon(result, iconSideLength, iconSideLength);
        label.setIcon(icon);
        label.setBorder(border);
        label.setText("<html><body>" + name + "<br><font size=\"-1\">" + ">>" + getParentPath(result) + "</body></html>");
        if (isChosen) {
            label.setBackground(labelColor);
        } else {
            label.setBackground(backgroundColor);
        }
    }
}
