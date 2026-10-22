package com.asuka.backend.service.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonContextExtractorTest {

    @Test
    void extractsFunctionOverlappingChangedLines() {
        String content = """
                import os
                def verify(token):
                    if not token:
                        return False
                    return True
                def other():
                    return 1
                """;
        PythonContextExtractor extractor = new PythonContextExtractor();

        List<ContextBlock> blocks = extractor.extract(content, Set.of(3, 4));

        assertFalse(blocks.isEmpty());
        assertTrue(blocks.stream().anyMatch(block -> block.content().contains("def verify")));
        assertTrue(extractor.imports(content).stream().anyMatch(line -> line.contains("import os")));
    }
}
