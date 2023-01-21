package file.engine.udisk.utils;

import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

public class VersionCheckUtil {
    private static final String CURRENT_VERSION = "2.3";
    private static String updateURL;

    private static JSONObject getVersionInfo() throws IOException {
        StringBuilder jsonUpdate = new StringBuilder();
        URL updateServer = new URL("https://cdn.jsdelivr.net/gh/XUANXUQAQ/File-Engine-Version/Plugins%20Repository/UDiskPluginVersion.json");
        URLConnection uc = updateServer.openConnection();
        uc.setConnectTimeout(3000);
        //防止屏蔽程序抓取而返回403错误
        uc.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36 Edg/109.0.1518.55");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream(), StandardCharsets.UTF_8))) {
            String eachLine;
            while ((eachLine = br.readLine()) != null) {
                jsonUpdate.append(eachLine);
            }
        }
        return JSONObject.parseObject(jsonUpdate.toString());
    }

    public static String _getUpdateURL() {
        return updateURL;
    }

    public static boolean _isLatest() throws IOException {
        JSONObject json = getVersionInfo();
        String latestVersion = json.getString("version");
        if (Double.parseDouble(latestVersion) > Double.parseDouble(CURRENT_VERSION)) {
            updateURL = json.getString("url");
            return false;
        } else {
            return true;
        }
    }

    public static String _getPluginVersion() {
        return CURRENT_VERSION;
    }
}
