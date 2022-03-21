package UDisk.utils;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.regex.Pattern;

public class RegexUtil {
    private static final ConcurrentSkipListMap<String, WeakReference<Pattern>> patternMap = new ConcurrentSkipListMap<>();

    private static final int MAX_PATTERN_CACHE_NUM = 10;

    /**
     * 获取正则表达式并放入缓存
     *
     * @param patternStr 正则表达式
     * @param flags      flags
     * @return 编译后的正则表达式
     */
    public static Pattern getPattern(String patternStr, int flags) {
        String key = patternStr + ":flags:" + flags;
        WeakReference<Pattern> pattern = patternMap.get(key);
        if (pattern == null) {
            Pattern compile = Pattern.compile(patternStr, flags);
            pattern = new WeakReference<>(compile);
            if (patternMap.size() < MAX_PATTERN_CACHE_NUM) {
                patternMap.put(key, pattern);
            } else {
                patternMap.remove(patternMap.firstEntry().getKey());
            }
            return compile;
        }
        Pattern compile = pattern.get();
        if (compile == null) {
            compile = Pattern.compile(patternStr, flags);
            pattern = new WeakReference<>(compile);
            if (patternMap.size() < MAX_PATTERN_CACHE_NUM) {
                patternMap.put(key, pattern);
            } else {
                patternMap.remove(patternMap.firstEntry().getKey());
            }
        }
        return compile;
    }
}
