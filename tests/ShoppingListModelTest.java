package tw.yc.smartshopping;

import java.lang.reflect.Method;
import java.util.TimeZone;

public final class ShoppingListModelTest {
    public static void main(String[] args) {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            assertEquals("採買清單 1970-01-01 00:00:00", ShoppingListModel.nameAt(0));
            testAppendOrderFollowsLastSurvivingRow();
            testDraftClearsOnlyAfterSuccessfulAppend();
            System.out.println("ShoppingListModelTest PASS");
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private static void testAppendOrderFollowsLastSurvivingRow() {
        assertEquals(0, ShoppingListModel.nextSortOrder(-1, 0));
        assertEquals(4, ShoppingListModel.nextSortOrder(3, 0));
        assertEquals(6, ShoppingListModel.nextSortOrder(3, 2));
    }

    private static void testDraftClearsOnlyAfterSuccessfulAppend() {
        assertFalse(ShoppingListModel.shouldClearDraftAfterAppend(false, "牛奶", "牛奶"));
        assertTrue(ShoppingListModel.shouldClearDraftAfterAppend(true, "牛奶", "牛奶"));
        assertFalse(ShoppingListModel.shouldClearDraftAfterAppend(true, "牛奶", "牛奶 雞蛋"));
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }
}
