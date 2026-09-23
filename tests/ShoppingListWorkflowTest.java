package tw.yc.smartshopping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class ShoppingListWorkflowTest {
    public static void main(String[] args) {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            testAddTextAppendsAndKeepsDuplicates();
            testSwitchChangesTheTargetList();
            testSaveAndStartNewPreservesOldList();
            testClearCurrentListLeavesOtherListsAlone();
            testDeletingActiveListSelectsFreshList();
            System.out.println("ShoppingListWorkflowTest PASS");
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private static void testAddTextAppendsAndKeepsDuplicates() {
        FakeStore store = new FakeStore();
        store.createList("目前清單");
        store.items.get(1L).add(item("牛奶"));
        ShoppingListWorkflow workflow = new ShoppingListWorkflow(store, 1L);

        workflow.addText("牛奶,雞蛋,雞蛋");

        List<LocalParser.ItemDraft> rows = store.items.get(1L);
        assertEquals(4, rows.size());
        assertEquals("牛奶", rows.get(0).name);
        assertEquals("牛奶", rows.get(1).name);
        assertEquals("雞蛋", rows.get(2).name);
        assertEquals("雞蛋", rows.get(3).name);
    }

    private static void testSaveAndStartNewPreservesOldList() {
        FakeStore store = new FakeStore();
        store.createList("目前清單");
        store.items.get(1L).add(item("牛奶"));
        ShoppingListWorkflow workflow = new ShoppingListWorkflow(store, 1L);

        workflow.saveAndStartNew(0L);

        assertEquals(2L, workflow.activeListId());
        assertEquals(1, store.items.get(1L).size());
        assertEquals(0, store.items.get(2L).size());
        assertEquals("採買清單 1970-01-01 00:00:00", store.names.get(2L));
    }

    private static void testSwitchChangesTheTargetList() {
        FakeStore store = new FakeStore();
        store.createList("清單一");
        store.createList("清單二");
        ShoppingListWorkflow workflow = new ShoppingListWorkflow(store, 1L);

        if (!workflow.switchTo(2L)) throw new AssertionError("existing list could not be selected");
        workflow.addText("雞蛋");

        assertEquals(2L, workflow.activeListId());
        assertEquals(0, store.items.get(1L).size());
        assertEquals("雞蛋", store.items.get(2L).get(0).name);
        if (workflow.switchTo(99L)) throw new AssertionError("missing list was selected");
    }

    private static void testClearCurrentListLeavesOtherListsAlone() {
        FakeStore store = new FakeStore();
        store.createList("目前清單");
        store.createList("另一張清單");
        store.items.get(1L).add(item("牛奶"));
        store.items.get(2L).add(item("雞蛋"));
        store.imagePaths.put(1L, new ArrayList<>(List.of("/private/one.jpg")));
        store.imagePaths.put(2L, new ArrayList<>(List.of("/private/two.jpg")));
        ShoppingListWorkflow workflow = new ShoppingListWorkflow(store, 1L);

        List<String> deletedPaths = workflow.clearCurrentList();

        assertEquals(Arrays.asList("/private/one.jpg"), deletedPaths);
        assertEquals(0, store.items.get(1L).size());
        assertEquals(1, store.items.get(2L).size());
        assertEquals(Arrays.asList("/private/two.jpg"), store.imagePaths.get(2L));
    }

    private static void testDeletingActiveListSelectsFreshList() {
        FakeStore store = new FakeStore();
        store.createList("目前清單");
        store.createList("歷史清單");
        store.items.get(1L).add(item("牛奶"));
        store.items.get(2L).add(item("雞蛋"));
        ShoppingListWorkflow workflow = new ShoppingListWorkflow(store, 1L);

        workflow.deleteList(1L, 0L);

        assertEquals(3L, workflow.activeListId());
        if (store.items.containsKey(1L)) throw new AssertionError("deleted list still exists");
        assertEquals(1, store.items.get(2L).size());
        assertEquals(0, store.items.get(3L).size());
    }

    private static LocalParser.ItemDraft item(String name) {
        return new LocalParser.ItemDraft(name, "", "未指定", "其他", 0);
    }

    private static final class FakeStore implements ShoppingListWorkflow.Store {
        final Map<Long, List<LocalParser.ItemDraft>> items = new HashMap<>();
        final Map<Long, List<String>> imagePaths = new HashMap<>();
        final Map<Long, String> names = new HashMap<>();
        long nextId = 1;

        @Override public void appendItems(long listId, List<LocalParser.ItemDraft> newItems) {
            items.get(listId).addAll(newItems);
        }

        @Override public long createList(String name) {
            long id = nextId++;
            names.put(id, name);
            items.put(id, new ArrayList<>());
            imagePaths.put(id, new ArrayList<>());
            return id;
        }

        @Override public List<String> clearList(long listId) {
            List<String> paths = new ArrayList<>(imagePaths.get(listId));
            imagePaths.get(listId).clear();
            items.get(listId).clear();
            return paths;
        }

        @Override public List<String> deleteList(long listId) {
            List<String> paths = new ArrayList<>(imagePaths.get(listId));
            imagePaths.remove(listId);
            items.remove(listId);
            names.remove(listId);
            return paths;
        }

        @Override public boolean containsList(long listId) {
            return items.containsKey(listId);
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
