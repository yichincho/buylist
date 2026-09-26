package tw.yc.smartshopping;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ShoppingListModel {
    public static final String UNASSIGNED_STORE = "未指定";
    public static final String[] PRESET_STORES = {"好市多", "全聯", "菜市場"};

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

    /** Indices of rows ordered by group (first appearance), keeping the original order inside each group. */
    public static List<Integer> groupedOrder(List<String> groupKeys) {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < groupKeys.size(); i++) {
            List<Integer> group = groups.get(groupKeys.get(i));
            if (group == null) {
                group = new ArrayList<>();
                groups.put(groupKeys.get(i), group);
            }
            group.add(i);
        }
        List<Integer> order = new ArrayList<>();
        for (List<Integer> group : groups.values()) order.addAll(group);
        return order;
    }

    public static String normalizeStore(String store) {
        String value = store == null ? "" : store.trim();
        return value.isEmpty() ? UNASSIGNED_STORE : value;
    }

    /** Presets, then custom stores, then stores already used; "未指定" is always last. */
    public static List<String> storeOptions(List<String> customStores, List<String> usedStores) {
        Set<String> options = new LinkedHashSet<>();
        Collections.addAll(options, PRESET_STORES);
        if (customStores != null) for (String store : customStores) options.add(normalizeStore(store));
        if (usedStores != null) for (String store : usedStores) options.add(normalizeStore(store));
        options.remove(UNASSIGNED_STORE);
        List<String> result = new ArrayList<>(options);
        result.add(UNASSIGNED_STORE);
        return result;
    }

    public static String encodeStores(List<String> stores) {
        StringBuilder text = new StringBuilder();
        for (String store : stores) {
            String value = store == null ? "" : store.trim().replace('\n', ' ');
            if (value.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }
        return text.toString();
    }

    public static List<String> decodeStores(String text) {
        List<String> stores = new ArrayList<>();
        if (text == null) return stores;
        for (String line : text.split("\n")) {
            String value = line.trim();
            if (!value.isEmpty() && !stores.contains(value)) stores.add(value);
        }
        return stores;
    }
}
