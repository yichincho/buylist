package tw.yc.smartshopping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalParser {
    private static final Pattern QUANTITY = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?\\s*\\S*)$");

    private LocalParser() {}

    public static List<ItemDraft> parse(String text) {
        return parse(text, Collections.<String>emptyList());
    }

    /**
     * Splits text into items. A segment that is only a store name (e.g. "全聯" or "好市多：")
     * starts a section: following items get that store until the next store heading.
     */
    public static List<ItemDraft> parse(String text, List<String> extraStores) {
        List<ItemDraft> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        List<String> stores = knownStores(extraStores);
        String[] parts = text.split("[\\n,，、;；]+");
        String sectionStore = null;
        int index = 0;
        for (String raw : parts) {
            String value = raw.trim().replaceFirst("^[-*•\\d.、)）]+\\s*", "");
            if (value.isEmpty()) continue;
            String heading = storeHeading(value, stores);
            if (heading != null) {
                sectionStore = heading;
                value = value.substring(headingText(value, heading).length()).replaceFirst("^[\\s:：]+", "").trim();
                if (value.isEmpty()) continue;
            }
            String quantity = "";
            Matcher matcher = QUANTITY.matcher(value);
            if (matcher.matches()) {
                value = matcher.group(1).trim();
                quantity = matcher.group(2).trim();
            }
            String store = storeIn(value, stores);
            if (store == null) store = sectionStore == null ? "未指定" : sectionStore;
            result.add(new ItemDraft(value, quantity, store, "其他", index++));
        }
        return result;
    }

    /** Returns copies of the items assigned to the given store; null or blank keeps the original stores. */
    public static List<ItemDraft> withStore(List<ItemDraft> items, String store) {
        if (store == null || store.trim().isEmpty() || items == null) return items;
        List<ItemDraft> result = new ArrayList<>();
        for (ItemDraft item : items) {
            result.add(new ItemDraft(item.name, item.quantity, store.trim(), item.category, item.originalIndex));
        }
        return result;
    }

    private static List<String> knownStores(List<String> extraStores) {
        List<String> stores = new ArrayList<>();
        Collections.addAll(stores, "好市多", "全聯", "菜市場");
        if (extraStores != null) {
            for (String store : extraStores) {
                String value = store == null ? "" : store.trim();
                if (!value.isEmpty() && !"未指定".equals(value) && !stores.contains(value)) stores.add(value);
            }
        }
        return stores;
    }

    private static String storeIn(String value, List<String> stores) {
        if (value.toLowerCase(Locale.ROOT).contains("costco")) return "好市多";
        for (String store : stores) if (value.contains(store)) return store;
        return null;
    }

    /** Store named at the start of a segment as a heading: "全聯", "全聯：牛奶" or "costco:". */
    private static String storeHeading(String value, List<String> stores) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("costco") || lower.startsWith("costco:") || lower.startsWith("costco：")) return "好市多";
        for (String store : stores) {
            if (value.equals(store) || value.startsWith(store + ":") || value.startsWith(store + "：")) return store;
        }
        return null;
    }

    private static String headingText(String value, String store) {
        return value.toLowerCase(Locale.ROOT).startsWith("costco") ? value.substring(0, 6) : store;
    }

    public static String appendVoice(String existing, String spoken) {
        String left = existing == null ? "" : existing.trim();
        String right = spoken == null ? "" : spoken.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + "\n" + right;
    }

    public static final class ItemDraft {
        public final String name, quantity, store, category;
        public final int originalIndex;
        public ItemDraft(String name, String quantity, String store, String category, int originalIndex) {
            this.name = name; this.quantity = quantity; this.store = store;
            this.category = category; this.originalIndex = originalIndex;
        }
    }
}

