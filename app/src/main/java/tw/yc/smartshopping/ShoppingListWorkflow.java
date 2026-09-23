package tw.yc.smartshopping;

import java.util.Collections;
import java.util.List;

public final class ShoppingListWorkflow {
    private final Store store;
    private long activeListId;

    public ShoppingListWorkflow(Store store, long initialListId) {
        if (store == null) throw new IllegalArgumentException("store is required");
        if (!store.containsList(initialListId)) throw new IllegalArgumentException("initial list does not exist");
        this.store = store;
        this.activeListId = initialListId;
    }

    public long activeListId() {
        return activeListId;
    }

    public boolean switchTo(long listId) {
        if (!store.containsList(listId)) return false;
        activeListId = listId;
        return true;
    }

    public int addText(String text) {
        List<LocalParser.ItemDraft> items = LocalParser.parse(text);
        if (items.isEmpty()) return 0;
        store.appendItems(activeListId, items);
        return items.size();
    }

    public long saveAndStartNew(long timestampMillis) {
        activeListId = store.createList(ShoppingListModel.nameAt(timestampMillis));
        return activeListId;
    }

    public List<String> clearCurrentList() {
        List<String> paths = store.clearList(activeListId);
        return paths == null ? Collections.<String>emptyList() : paths;
    }

    public List<String> deleteList(long listId, long timestampMillis) {
        if (!store.containsList(listId)) return Collections.emptyList();
        List<String> paths = store.deleteList(listId);
        if (activeListId == listId) {
            activeListId = store.createList(ShoppingListModel.nameAt(timestampMillis));
        }
        return paths == null ? Collections.<String>emptyList() : paths;
    }

    public interface Store {
        void appendItems(long listId, List<LocalParser.ItemDraft> items);
        long createList(String name);
        List<String> clearList(long listId);
        List<String> deleteList(long listId);
        boolean containsList(long listId);
    }
}
