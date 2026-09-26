package tw.yc.smartshopping;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public final class ShoppingListModelTest {
    public static void main(String[] args) {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            assertEquals("採買清單 1970-01-01 00:00:00", ShoppingListModel.nameAt(0));
            testAppendOrderFollowsLastSurvivingRow();
            testDraftClearsOnlyAfterSuccessfulAppend();
            testGroupedOrderKeepsFirstAppearance();
            testStoreOptions();
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

    private static void testGroupedOrderKeepsFirstAppearance() {
        List<String> keys = Arrays.asList("全聯", "好市多", "全聯", "未指定", "好市多");
        assertEquals(Arrays.asList(0, 2, 1, 4, 3), ShoppingListModel.groupedOrder(keys));
    }

    private static void testStoreOptions() {
        assertEquals(Arrays.asList("好市多", "全聯", "菜市場", "家樂福", "屈臣氏", "未指定"),
                ShoppingListModel.storeOptions(Arrays.asList("家樂福", " "), Arrays.asList("全聯", "屈臣氏", "未指定")));
        assertEquals("家樂福\n傳統市場", ShoppingListModel.encodeStores(Arrays.asList(" 家樂福 ", "", "傳統市場")));
        assertEquals(Arrays.asList("家樂福", "傳統市場"), ShoppingListModel.decodeStores("家樂福\n\n傳統市場\n家樂福"));
        assertEquals("未指定", ShoppingListModel.normalizeStore("  "));
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
