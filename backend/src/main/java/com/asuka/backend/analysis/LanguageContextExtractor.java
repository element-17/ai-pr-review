package com.asuka.backend.analysis;

import java.util.List;
import java.util.Set;

/**
 * 轻量语言上下文提取器。
 */
public interface LanguageContextExtractor {

    String language();

    List<String> imports(String content);

    List<ContextBlock> extract(String content, Set<Integer> addedLines);
}
