package com.asuka.backend.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatchLineIndexTest {

    @Test
    void parsesAddedLinesAndHunkLines() {
        String patch = """
                @@ -10,7 +10,12 @@ export function verifyToken(token: string) {
                -  if (!token) return true;
                +  if (!token) {
                +    throw new UnauthorizedError();
                +  }
                   return true;
                """;

        PatchLineIndex index = PatchLineIndex.parse(patch);

        assertTrue(index.overlapsAdded(10, 12));
        assertTrue(index.overlapsAdded(12, 18));
        assertFalse(index.overlapsAdded(1, 5));
        assertTrue(index.overlapsHunk(13, 13));
    }

    @Test
    void handlesEmptyPatch() {
        PatchLineIndex index = PatchLineIndex.parse(null);
        assertFalse(index.overlapsAdded(1, 1));
        assertFalse(index.overlapsHunk(1, 1));
    }
}
