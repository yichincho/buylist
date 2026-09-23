package tw.yc.smartshopping;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ShoppingListModel {
    private ShoppingListModel() {}

    public static String nameAt(long timestampMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.TAIWAN);
        return "採買清單 " + format.format(new Date(timestampMillis));
    }

    public static int nextSortOrder(int lastExistingOrder, int offset) {
        return lastExistingOrder + 1 + offset;
    }

    public static boolean shouldClearDraftAfterAppend(boolean appendSucceeded, String submittedText, String currentText) {
        return appendSucceeded && submittedText != null && submittedText.equals(currentText);
    }
}
