package com.asuka.backend.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchDiffParserTest {

    @Test
    void parsesHunkAndLineNumbers() {
        String patch = String.join("\n",
                "diff --git a/src/auth.ts b/src/auth.ts",
                "index 1111111..2222222 100644",
                "--- a/src/auth.ts",
                "+++ b/src/auth.ts",
                "@@ -1,3 +1,4 @@",
                " const enabled = true;",
                "-const token = null;",
                "+const token = readToken();",
                "+const retry = 1;");

        PatchDiffParser.Parsed parsed = PatchDiffParser.parse(patch);

        assertEquals(List.of(2, 3), parsed.addedLines());
        assertEquals(List.of(1, 2, 3), parsed.hunkLines());
        assertEquals(9, parsed.rows().size());
        assertEquals("hunk", parsed.rows().get(4).type());
        assertEquals(Integer.valueOf(1), parsed.rows().get(5).oldLine());
        assertEquals(Integer.valueOf(1), parsed.rows().get(5).newLine());
        assertEquals("delete", parsed.rows().get(6).type());
        assertEquals(Integer.valueOf(2), parsed.rows().get(6).oldLine());
        assertEquals(null, parsed.rows().get(6).newLine());
        assertEquals("add", parsed.rows().get(7).type());
        assertEquals(Integer.valueOf(2), parsed.rows().get(7).newLine());
        assertEquals("const token = readToken();", parsed.rows().get(7).content());
        assertEquals(Integer.valueOf(3), parsed.rows().get(8).newLine());
    }

    @Test
    void emptyPatchReturnsEmptyRows() {
        PatchDiffParser.Parsed parsed = PatchDiffParser.parse(null);
        assertTrue(parsed.rows().isEmpty());
        assertTrue(parsed.addedLines().isEmpty());
        assertTrue(parsed.hunkLines().isEmpty());
    }
}
