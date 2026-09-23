package tw.yc.smartshopping;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalParser {
    private static final Pattern QUANTITY = Pattern.compile("^(.+?)\\s+(\\d+(?:\\.\\d+)?\\s*\\S*)$");

    private LocalParser() {}

    public static List<ItemDraft> parse(String text) {
        List<ItemDraft> result = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return result;
        String[] parts = text.split("[\\n,，、;；]+");
        int index = 0;
        for (String raw : parts) {
            String value = raw.trim().replaceFirst("^[-*•\\d.、)）]+\\s*", "");
            if (value.isEmpty()) continue;
            String quantity = "";
            Matcher matcher = QUANTITY.matcher(value);
            if (matcher.matches()) {
                value = matcher.group(1).trim();
                quantity = matcher.group(2).trim();
            }
            String store = value.contains("好市多") || value.toLowerCase().contains("costco") ? "好市多"
                    : value.contains("全聯") ? "全聯" : "未指定";
            result.add(new ItemDraft(value, quantity, store, "其他", index++));
        }
        return result;
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

