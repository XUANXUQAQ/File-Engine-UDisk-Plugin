package UDisk;

import UDisk.DllInterface.GetAscII;
import UDisk.GetIcon.GetIconUtil;
import UDisk.SqliteConfig.SQLiteUtil;
import UDisk.VersionCheck.VersionCheckUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static UDisk.OpenFile.OpenFileUtil.openWithAdmin;
import static UDisk.OpenFile.OpenFileUtil.openWithoutAdmin;
import static UDisk.Search.SearchUDisk.searchFiles;

public class PluginMain extends Plugin {
    private final String databaseRelativePath = "plugins/Plugin configuration files/UDisk/data.db";
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
    private Color pluginLabelColor = new Color(0xcccccc);
    private Color pluginBackgroundColor = new Color(0x333333);
    private volatile String text;
    private volatile int openLastFolderKeyCode;
    private volatile int runAsAdminKeyCode;
    private volatile int copyPathKeyCode;
    private boolean timer = false;
    private final HashMap<String, Boolean> mapDiskExist = new HashMap<>();

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

    private void initAllSettings() {
        String line;
        StringBuilder strb = new StringBuilder();
        String settingsRelativePath = "user/settings.json";
        File settings = new File(settingsRelativePath);
        try (BufferedReader buffr = new BufferedReader(new InputStreamReader(new FileInputStream(settings), StandardCharsets.UTF_8))) {
            while ((line = buffr.readLine()) != null) {
                strb.append(line);
            }
            JSONObject settingsInJson = JSON.parseObject(strb.toString());
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
        String each;
        String pSql = "SELECT PATH FROM " + column + ";";
        try (PreparedStatement pStmt = SQLiteUtil.getConnection().prepareStatement(pSql);
             ResultSet resultSet = pStmt.executeQuery()) {
            while (resultSet.next()) {
                each = resultSet.getString("PATH");
                checkIsMatchedAndAddToList(each);
                //用户重新输入了信息
                if (startTime > time) {
                    break;
                }
            }
        }
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
            mapDiskExist.put(str, file.exists());
        }
    }

    private String[] checkUDisk() {
        File file ;
        StringBuilder disk = new StringBuilder();
        for(;;) {
            for(String str : arr) {
                file = new File(str + ":\\");
                // 如果磁盘现在存在，并且以前不存在
                // 则表示刚插上U盘，返回
                if(file.exists() && !mapDiskExist.get(str)) {
                    disk.append(str).append(";");
                }

                if(file.exists() != mapDiskExist.get(str)) {
                    mapDiskExist.put(str, file.exists());
                }
            }
            String disks = disk.toString();
            if (!disks.isEmpty()) {
                return semicolon.split(disks);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void addCommandsByAsciiGroup(int asciiGroup) {
        String command;
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
    }

    private void initThreadPool() {
        threadPool.execute(() -> {
            String[] disks;
            while (isNotExit) {
                disks = checkUDisk();
                for (String UDisk : disks) {
                    displayMessage("提示", "输入 " + "\"" + " >udisk >" + UDisk + "\"" + " 来创建索引");
                }
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
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            } catch (InterruptedException ignored) {

            }
        });

        threadPool.execute(() -> { //添加sql命令线程
            try {
                long endTime;
                while (isNotExit) {
                    endTime = System.currentTimeMillis();
                    if ((endTime - startTime > 500) && (timer) && !isIndexMode) {
                        timer = false;
                        String name = getFileName(searchText.toUpperCase());
                        int ascII = getAscIISum(name);
                        int asciiGroup = ascII / 100;

                        addCommandsByAsciiGroup(asciiGroup);
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
                                            displayMessage("提示", "搜索完成");
                                        }
                                    } catch (IOException | InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } catch (StringIndexOutOfBoundsException ignored) {
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(50);
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    //Do Not Remove
    @SuppressWarnings("unused")
    public String[] getMessage() {
        return _getMessage();
    }

    //Do Not Remove
    @SuppressWarnings("unused")
    public String pollFromResultQueue() {
        return _pollFromResultQueue();
    }

    //Do Not Remove
    @SuppressWarnings("unused")
    public int getApiVersion() {
        return _getApiVersion();
    }

    //Do Not Remove
    @SuppressWarnings("unused")
    public void clearResultQueue() {
        _clearResultQueue();
    }

    /**
     * Do Not Remove, this is used for File-Engine to replace the handler which the plugin is registered.
     * The object array contains two parts.
     * object[0] contains the fully-qualified name of class.
     * object[1] contains a consumer to hande the event.
     *
     * @return Event handler
     * @see #replaceFileEngineEventHandler(String, BiConsumer)
     */
    @SuppressWarnings("unused")
    public Object[] pollFromEventHandlerQueue() {
        return _pollEventHandlerQueue();
    }

    /**
     * Do Not Remove, this is used for File-Engine to restore the handler which the plugin is registered.
     * @see #restoreFileEngineEventHandler(String)
     * @return Event class fully-qualified name
     */
    @SuppressWarnings("unused")
    public String restoreFileEngineEventHandler() {
        return _pollFromRestoreQueue();
    }

    @Override
    public void setCurrentTheme(int defaultColor, int choseLabelColor, int borderColor) {
        pluginBackgroundColor = new Color(defaultColor);
        pluginLabelColor = new Color(choseLabelColor);
    }

    @Override
    public void searchBarVisible(String showingMode) {

    }

    @Override
    public void configsChanged(Map<String, Object> configs) {

    }

    @Override
    public void eventProcessed(Class<?> c, Object eventInstance) {
        if ("file.engine.event.handler.impl.database.StartSearchEvent".equals(c.getName())) {
            try {
                Field searchTextField = c.getDeclaredField("searchText");
                Field searchCaseField = c.getDeclaredField("searchCase");
                Field keywordsField = c.getDeclaredField("keywords");
                Supplier<String> searchTextSupplier = (Supplier<String>) searchTextField.get(eventInstance);
                Supplier<String[]> searchCaseSupplier = (Supplier<String[]>) searchCaseField.get(eventInstance);
                Supplier<String[]> keywordsSupplier = (Supplier<String[]>) keywordsField.get(eventInstance);
                searchText = searchTextSupplier.get();
                searchCase = searchCaseSupplier.get();
                keywords = keywordsSupplier.get();
                isIndexMode = false;
                commandQueue.clear();
                timer = true;
                startTime = System.currentTimeMillis();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
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

    }

    @Override
    public void loadPlugin(Map<String, Object> configs) {
        try {
            String configurationPath = "plugins/Plugin configuration files/UDisk";
            File pluginFolder = new File(configurationPath);
            if (!pluginFolder.exists()) {
                pluginFolder.mkdirs();
            }

            CopyFileUtil.copyFile(PluginMain.class.getResourceAsStream("/fileSearcher.exe"), new File(configurationPath, "fileSearcher.exe"));

            initAllSettings();

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
            isNotExit = false;
            threadPool.shutdown();
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
            openFile(result);
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
        openFile(result);
    }

    private void openFile(String result) {
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
            openWithoutAdmin(result);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e, String result) {}

    @Override
    public ImageIcon getPluginIcon() {
        return new ImageIcon(Objects.requireNonNull(PluginMain.class.getResource("/icon.png")));
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
    public boolean isLatest() throws Exception {
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
