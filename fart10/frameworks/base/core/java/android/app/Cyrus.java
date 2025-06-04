package android.app;

import android.util.Log;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Cyrus {

    private static final String TAG = "Cyrus";
    private static boolean initialized = false;

    private static boolean dumpEnabled = false;
    private static int sleepTimeMs = 60 * 1000;
    private static List<Pattern> forceCallClassPatterns = new ArrayList<>();
    private static List<Pattern> ignoredClassPatterns = new ArrayList<>();

    /**
     * 初始化 Cyrus 配置
     * 从 /data/data/{packageName}/cyrus.config 读取配置项：
     * dump, sleep, force, ignore
     *
     * @param packageName 应用包名
     */
    public static void init(String packageName) {
        if (initialized) return;

        File configFile = new File("/data/data/" + packageName + "/cyrus.config");
        if (!configFile.exists()) {
            Log.w(TAG, "Config file not found: " + configFile.getPath());
            initialized = true;
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("dump=")) {
                    dumpEnabled = line.substring(5).equalsIgnoreCase("true");
                } else if (line.startsWith("sleep=")) {
                    sleepTimeMs = Integer.parseInt(line.substring(6));
                } else if (line.startsWith("force=")) {
                    String[] parts = line.substring(6).split(",");
                    for (String part : parts) {
                        forceCallClassPatterns.add(Pattern.compile(convertToRegex(part)));
                    }
                } else if (line.startsWith("ignore=")) {
                    String[] parts = line.substring(7).split(",");
                    for (String part : parts) {
                        ignoredClassPatterns.add(Pattern.compile(convertToRegex(part)));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read config: " + e.getMessage(), e);
        }

        initialized = true;
    }

    /**
     * 是否启用脱壳功能
     * @return true 表示启用
     */
    public static boolean isDumpEnabled() {
        return dumpEnabled;
    }

    /**
     * 获取脱壳前的延迟休眠时间（毫秒）
     * @return 休眠时间（单位：毫秒）
     */
    public static int getSleepTimeMs() {
        return sleepTimeMs;
    }

    /**
     * 获取匹配主动调用类的正则规则列表
     * @return 正则 Pattern 列表
     */
    public static List<Pattern> getForceCallClassPatterns() {
        return forceCallClassPatterns;
    }

    /**
     * 获取忽略主动调用类的正则规则列表
     * @return 正则 Pattern 列表
     */
    public static List<Pattern> getIgnoredClassPatterns() {
        return ignoredClassPatterns;
    }

    /**
     * 判断一个类是否需要在脱壳线程启动时被主动调用。
     * <p>
     * 判断逻辑如下：
     * 1. 如果配置中设置了 force 规则（forceCallClassPatterns 非空）：
     *    - 只有匹配 force 列表中的类会返回 true，其余类返回 false。
     * 2. 如果未设置 force，但配置了 ignore 规则（ignoredClassPatterns 非空）：
     *    - 匹配 ignore 列表的类返回 false，其余返回 true。
     * 3. 如果 force 和 ignore 都为空：
     *    - 默认所有类都返回 true。
     * 4. 如果同时配置了 force 和 ignore，则优先判断 force
    */
    public static boolean shouldForceCall(String className) {
        if (!forceCallClassPatterns.isEmpty()) {
            for (Pattern force : forceCallClassPatterns) {
                if (force.matcher(className).matches()) {
                    return true;
                }
            }
            return false;
        }

        if (!ignoredClassPatterns.isEmpty()) {
            for (Pattern ignored : ignoredClassPatterns) {
                if (ignored.matcher(className).matches()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 将配置文件中的通配符路径转为正则表达式
     * 例如 ff.l0.* → ff\.l0\..*
     * @param pattern 原始配置字符串
     * @return 正则表达式字符串
     */
    private static String convertToRegex(String pattern) {
        // exact match or wildcard * support
        if (!pattern.contains("*")) {
            return Pattern.quote(pattern);
        }
        return pattern.replace(".", "\\.").replace("*", ".*");
    }
}
