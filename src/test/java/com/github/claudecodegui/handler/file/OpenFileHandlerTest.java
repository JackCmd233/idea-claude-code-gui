package com.github.claudecodegui.handler.file;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OpenFileHandlerTest {

    @Test
    public void parsesLineInfoForSimplePath() {
        OpenFileHandler.LineInfo lineInfo = OpenFileHandler.parseLineInfo("src/foo/bar.ts:42");

        assertEquals("src/foo/bar.ts", lineInfo.actualPath());
        assertEquals(42, lineInfo.lineNumber());
        assertEquals(-1, lineInfo.endLineNumber());
        assertTrue(lineInfo.hasLineInfo());
    }

    @Test
    public void parsesLineRangeInfo() {
        OpenFileHandler.LineInfo lineInfo = OpenFileHandler.parseLineInfo("Main.java:128-140");

        assertEquals("Main.java", lineInfo.actualPath());
        assertEquals(128, lineInfo.lineNumber());
        assertEquals(140, lineInfo.endLineNumber());
        assertTrue(lineInfo.hasLineInfo());
    }

    @Test
    public void rejectsColumnSyntax() {
        OpenFileHandler.LineInfo lineInfo = OpenFileHandler.parseLineInfo("E:\\project\\src\\Foo.java:42:15");

        assertEquals("E:\\project\\src\\Foo.java:42:15", lineInfo.actualPath());
        assertEquals(-1, lineInfo.lineNumber());
        assertEquals(-1, lineInfo.endLineNumber());
        assertFalse(lineInfo.hasLineInfo());
    }
}
