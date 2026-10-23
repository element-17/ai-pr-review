package com.asuka.backend.core.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeScriptContextExtractorTest {

    @Test
    void extractsFunctionOverlappingChangedLines() {
        String content = """
                import { helper } from './helper';
                export function verifyToken(token: string) {
                  if (!token) {
                    throw new Error('empty');
                  }
                  return helper(token);
                }
                export function other() {
                  return 1;
                }
                """;
        TypeScriptContextExtractor extractor = new TypeScriptContextExtractor();

        List<ContextBlock> blocks = extractor.extract(content, Set.of(2, 3));

        assertFalse(blocks.isEmpty());
        assertTrue(blocks.stream().anyMatch(block -> block.content().contains("verifyToken")));
        assertTrue(extractor.imports(content).stream().anyMatch(line -> line.contains("import")));
    }
}
