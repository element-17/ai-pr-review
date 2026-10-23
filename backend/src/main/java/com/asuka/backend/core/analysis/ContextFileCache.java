package com.asuka.backend.core.analysis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单个任务内的文件内容缓存。
 *
 * <p>任务结束后由 GC 回收；不把代码全文写入 Redis 或数据库。
 */
public class ContextFileCache {

    private static final int MAX_ENTRIES = 200;

    private final Map<String, String> files = new ConcurrentHashMap<>();

    public String get(String path, Supplier<String> loader) {
        String cached = files.get(path);
        if (cached != null) {
            return cached;
        }
        String content = loader.get();
        if (files.size() < MAX_ENTRIES) {
            files.put(path, content);
        }
        return content;
    }
}
