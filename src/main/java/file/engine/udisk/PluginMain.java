package file.engine.udisk;

import file.engine.udisk.DllInterface.IsLocalDisk;
import file.engine.udisk.utils.*;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static file.engine.udisk.utils.SearchUtil.searchFiles;

public class PluginMain extends Plugin {
    private static final String databaseRelativePath = "plugins/Plugin configuration files/UDisk/data.db";
    private final String[] arr = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
    private boolean isNotExit = true;
    private long startTime;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private volatile String[] searchCase;
    private volatile String searchText;
    private static volatile String[] keywords;
    private final ConcurrentLinkedQueue<String> commandQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isRunAsAdminPressed = false;
    private volatile boolean isCopyPathPressed = false;
    private volatile boolean isIndexMode = false;
    private volatile boolean isOpenLastFolderPressed = false;
    private int pluginIconSideLength = 0;
    private Color pluginLabelColor = new Color(0xcccccc);
    private Color pluginBackgroundColor = new Color(0x333333);
    private Color pluginFontColorWithCoverage = Color.BLACK;
    private volatile String text;
    private volatile int openLastFolderKeyCode;
    private volatile int runAsAdminKeyCode;
    private volatile int copyPathKeyCode;
    private boolean timer = false;
    private final HashMap<String, Boolean> mapDiskExist = new HashMap<>();

    private void initDll() {
        try {
            Class.forName("file.engine.udisk.DllInterface.IsLocalDisk");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private boolean isExist(String path) {
        File f = new File(path);
        return f.exists();
    }

    private void checkIsMatchedAndAddToList(String path) {
        if (PathMatchUtil.check(path, searchCase, searchText, keywords)) {
            if (isExist(path)) {
                addToResultQueue(path);
            }
        }
    }

    private void addResult(long time, String column) throws SQLException {
        //为label添加结果
        String each;
        String pSql = "SELECT PATH FROM " + column + ";";
        try (Statement pStmt = SQLiteUtil.getConnection().createStatement();
             ResultSet resultSet = pStmt.executeQuery(pSql)) {
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

    // 初始化磁盘状态，存在true， 否则false
    private void initDiskStatus() {
        File file;
        for (String str : arr) {
            file = new File(str + ":\\");
            mapDiskExist.put(str, file.exists());
        }
    }

    private String[] checkUDisk() {
        File file;
        StringBuilder disk = new StringBuilder();
        for (; ; ) {
            for (String str : arr) {
                file = new File(str + ":\\");
                // 如果磁盘现在存在，并且以前不存在
                // 则表示刚插上U盘，返回
                if (file.exists() && !mapDiskExist.get(str)) {
                    disk.append(str).append(";");
                }

                if (file.exists() != mapDiskExist.get(str)) {
                    mapDiskExist.put(str, file.exists());
                }
            }
            String disks = disk.toString();
            if (!disks.isEmpty()) {
                return RegexUtil.semicolon.split(disks);
            }

            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private void addTables() {
        String command;
        for (int i = 0; i <= 40; i++) {
            command = "list" + i;
            commandQueue.add(command);
        }
    }

    private void initThreadPool() {
        threadPool.execute(() -> {
            String[] disks;
            while (isNotExit) {
                disks = checkUDisk();
                for (String UDisk : disks) {
                    if (IsLocalDisk.INSTANCE.isDiskNTFS(UDisk + ":\\")) {
                        displayMessage("提示", "检测到插入的磁盘为NTFS格式，可以加入到主程序进行索引，无需使用插件");
                    } else {
                        displayMessage("提示", "输入 " + "\"" + " >udisk >" + UDisk + "\"" + " 来创建索引");
                    }
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        threadPool.execute(() -> { //添加sql命令线程
            try {
                long endTime;
                while (isNotExit) {
                    endTime = System.currentTimeMillis();
                    if ((endTime - startTime > 500) && (timer) && !isIndexMode) {
                        timer = false;
                        addTables();
                    } else if ((endTime - startTime > 500) && (timer) && isIndexMode) {
                        timer = false;
                        isIndexMode = false;
                        try {
                            if (text != null) {
                                String searchPath = text.charAt(1) + ":";
                                if (!":".equals(searchPath)) {
                                    try {
                                        File file = new File(searchPath + "\\");
                                        if (file.exists()) {
                                            if (IsLocalDisk.INSTANCE.isDiskNTFS(file.getAbsolutePath())) {
                                                displayMessage("提示", "该磁盘为NTFS格式，可以添加进入主程序进行搜索，速度相较于插件更快");
                                            } else {
                                                searchFiles(searchPath);
                                                displayMessage("提示", "搜索完成");
                                            }
                                        }
                                    } catch (IOException | InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } catch (StringIndexOutOfBoundsException e) {
                            e.printStackTrace();
                        }
                    }
                    TimeUnit.MILLISECONDS.sleep(50);
                }
            } catch (Exception e) {
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
     *
     * @return Event class fully-qualified name
     * @see #restoreFileEngineEventHandler(String)
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
        isRunAsAdminPressed = false;
        isOpenLastFolderPressed = false;
        isCopyPathPressed = false;
    }

    @Override
    public void configsChanged(Map<String, Object> configs) {
        initAllSettings(configs);
    }

    private void initAllSettings(Map<String, Object> configs) {
        final int colorHex = (int) configs.getOrDefault("fontColorWithCoverage", 0);
        pluginFontColorWithCoverage = new Color(colorHex);
        openLastFolderKeyCode = (int) configs.getOrDefault("openLastFolderKeyCode", 17);
        runAsAdminKeyCode = (int) configs.getOrDefault("runAsAdminKeyCode", 16);
        copyPathKeyCode = (int) configs.getOrDefault("copyPathKeyCode", 18);
    }

    @SuppressWarnings("unchecked")
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
                strings = RegexUtil.getPattern(":", 0).split(_text);
                length = strings.length;
                if (length == 2) {
                    searchCase = RegexUtil.semicolon.split(strings[1].toLowerCase());
                    searchText = strings[0];
                } else {
                    searchText = strings[0];
                    searchCase = null;
                }
                keywords = RegexUtil.semicolon.split(searchText);
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
                if (!pluginFolder.mkdirs()) {
                    throw new RuntimeException("mkdir " + pluginFolder + "failed.");
                }
            }
            initAllSettings(configs);
            Path databaseFilePath = Path.of(databaseRelativePath);
            if (Files.exists(databaseFilePath)) {
                long length = Files.size(databaseFilePath);
                if (length > 5L * 1024 * 1024 * 100) {
                    Files.delete(databaseFilePath);
                }
            }
            SQLiteUtil.initConnection("jdbc:sqlite:" + databaseFilePath.toAbsolutePath());
            SQLiteUtil.createAllTables();

            initDll();
            initDiskStatus();
            System.out.println("UDisk: starting thread pool...");
            initThreadPool();
            System.out.println("UDisk: init done.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void unloadPlugin() {
        try {
            isNotExit = false;
            threadPool.shutdown();
        } catch (Exception e) {
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
        } else if (openLastFolderKeyCode == key) {
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
            OpenFileUtil.openWithAdmin(result);
        } else if (isCopyPathPressed) {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            Transferable trans = new StringSelection(result);
            clipboard.setContents(trans, null);
        } else {
            OpenFileUtil.openWithoutAdmin(result);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e, String result) {
    }

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
        return "icon: https://icons8.com/icon/VUbZIzgZCtQe/u " +
                "icon by https://icons8.com Icons8</a>\n" +
                "中文说明：\n" +
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

    /**
     * @param label JLabel
     * @return 计算出的每个label可显示的最大字符数量
     */
    private int getMaxShowCharsNum(JLabel label) {
        int fontSize = (int) ((label.getFont().getSize() / 96.0f * 72) / 2);
        return Math.max(label.getWidth() / fontSize, 20);
    }

    /**
     * 高亮显示
     *
     * @param html     待处理的html
     * @param keywords 高亮关键字
     * @return 处理后带html
     */
    private String highLight(String html, String[] keywords) {
        StringBuilder builder = new StringBuilder();
        List<String> collect = Arrays.stream(keywords).sorted((o1, o2) -> o2.length() - o1.length()).collect(Collectors.toList());
        for (String keyword : collect) {
            if (!keyword.isBlank()) {
                builder.append(keyword).append("|");
            }
        }
        // 挑出所有的中文字符
        Map<String, String> chinesePinyinMap = PinyinUtil.getChinesePinyinMap(html);
        // 转换成拼音后和keywords匹配，如果发现匹配出成功，则添加到正则表达式中
        chinesePinyinMap.entrySet()
                .stream()
                .filter(pair -> Arrays.stream(keywords)
                        .anyMatch(each -> each.toLowerCase(Locale.ROOT).contains(pair.getValue().toLowerCase(Locale.ROOT))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .forEach((k, v) -> builder.append(k).append("|"));
        if (builder.length() > 0) {
            String pattern = builder.substring(0, builder.length() - 1);
            Pattern compile = RegexUtil.getPattern(pattern, Pattern.CASE_INSENSITIVE);
            Matcher matcher = compile.matcher(html);
            html = matcher.replaceAll((matchResult) -> {
                String group = matchResult.group();
                String s = "#" + ColorUtil.parseColorHex(pluginFontColorWithCoverage);
                return "<span style=\"color: " + s + ";\">" + group + "</span>";
            });
            return html;
        }
        return html;
    }

    /**
     * 根据path或command生成显示html
     *
     * @param path path
     * @return html
     */
    private String getHtml(String path, boolean[] isParentPathEmpty, JLabel label) {
        String template = "<html><body>%s</body></html>";
        isParentPathEmpty[0] = false;
        // 普通模式
        int maxShowCharNum = getMaxShowCharsNum(label);
        String parentPath = FileUtil.getParentPath(path);
        String fileName = FileUtil.getFileName(path);
        int blankNUm = 20;
        int charNumbers = fileName.length() + parentPath.length() + 20;
        if (charNumbers > maxShowCharNum) {
            parentPath = getContractPath(parentPath, maxShowCharNum);
            isParentPathEmpty[0] = parentPath.isEmpty();
        } else {
            blankNUm = Math.max(maxShowCharNum - fileName.length() - parentPath.length() - 20, 20);
        }
        return String.format(template,
                "<div>" +
                        highLight(fileName, keywords) +
                        "<font size=\"-2\">" +
                        getBlank(blankNUm) + parentPath +
                        "</font>" +
                        "</div>");
    }

    /**
     * 在路径中添加省略号
     *
     * @param path               path
     * @param maxShowingCharsNum 最大可显示字符数量
     * @return 生成后的字符串
     */
    private String getContractPath(String path, int maxShowingCharsNum) {
        String[] split = RegexUtil.getPattern("\\\\", 0).split(path);
        StringBuilder tmpPath = new StringBuilder();
        int contractLimit = 35;
        for (String tmp : split) {
            if (tmp.length() > contractLimit) {
                tmpPath.append(tmp, 0, contractLimit).append("...").append("\\");
            } else {
                tmpPath.append(tmp).append("\\");
            }
        }
        if (tmpPath.length() > maxShowingCharsNum) {
            return "";
        }
        return tmpPath.toString();
    }

    private String getBlank(int num) {
        return "&nbsp;".repeat(Math.max(0, num));
    }

    @Override
    public void showResultOnLabel(String result, JLabel label, boolean isChosen) {
        if (pluginIconSideLength == 0) {
            pluginIconSideLength = label.getHeight() / 3;
        }
        //将文件的路径信息存储在label的名称中，在未被选中时只显示文件名，选中后才显示文件路径
        boolean[] isParentPathEmpty = new boolean[1];
        String allHtml = getHtml(result, isParentPathEmpty, label);
        if (isParentPathEmpty[0]) {
            int maxShowCharsNum = getMaxShowCharsNum(label);
            boolean isContract = result.length() > maxShowCharsNum;
            int subNum = Math.max(0, maxShowCharsNum - "...".length() - 20);
            subNum = Math.min(result.length(), subNum);
            String showPath = isContract ? result.substring(0, subNum) : result;
            String add = isContract ? "..." : "";
            label.setName("<html><body>" + highLight(FileUtil.getFileName(result), keywords) + getBlank(20) +
                    "<font size=\"-2\">" + showPath + add + "</font></body></html>");
        }
        label.setText(allHtml);
        ImageIcon icon = GetIconUtil.getBigIcon(result, pluginIconSideLength, pluginIconSideLength);
        label.setIcon(icon);
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
