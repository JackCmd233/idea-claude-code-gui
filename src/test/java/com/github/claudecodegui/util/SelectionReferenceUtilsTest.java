package com.github.claudecodegui.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests for editor selection reference formatting.
 */
public class SelectionReferenceUtilsTest {

    @Test
    public void formatReference_returnsSingleLineReference() {
        assertEquals(
            "@D:/Code/demo/App.java#L12",
            SelectionReferenceUtils.formatReference("D:/Code/demo/App.java", 12, 12)
        );
    }

    @Test
    public void formatReference_returnsRangeReference() {
        assertEquals(
            "@D:/Code/demo/App.java#L12-18",
            SelectionReferenceUtils.formatReference("D:/Code/demo/App.java", 12, 18)
        );
    }

    @Test
    public void formatReference_returnsNullForBlankPath() {
        assertNull(SelectionReferenceUtils.formatReference("  ", 1, 1));
    }

    @Test
    public void formatReference_returnsNullForInvalidLineRange() {
        assertNull(SelectionReferenceUtils.formatReference("D:/Code/demo/App.java", 5, 4));
    }
}
