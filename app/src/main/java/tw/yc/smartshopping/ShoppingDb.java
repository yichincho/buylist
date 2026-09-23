package tw.yc.smartshopping;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ShoppingDb extends SQLiteOpenHelper {
    private final Context appContext;

    public ShoppingDb(Context context) {
        super(context, "shopping.db", null, 2);
        this.appContext = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE shopping_lists(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        long now = System.currentTimeMillis();
        ContentValues list = new ContentValues();
        list.put("name", "採買清單"); list.put("created_at", now); list.put("updated_at", now);
        db.insertOrThrow("shopping_lists", null, list);
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,quantity TEXT NOT NULL,store TEXT NOT NULL,category TEXT NOT NULL,sort_order INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,list_id INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE images(id INTEGER PRIMARY KEY AUTOINCREMENT,path TEXT NOT NULL,sort_order INTEGER NOT NULL,done INTEGER NOT NULL DEFAULT 0,list_id INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE INDEX idx_items_list_sort ON items(list_id,sort_order,id)");
        db.execSQL("CREATE INDEX idx_images_list_sort ON images(list_id,sort_order,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) {
            runMigration(db);
            return;
        }
        throw new IllegalStateException("Unsupported database upgrade from " + oldVersion + " to " + newVersion);
    }

    private void runMigration(SQLiteDatabase db) {
        StringBuilder script = new StringBuilder();
        try (InputStream input = appContext.getResources().openRawResource(R.raw.migrate_v1_v2);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) script.append(line).append('\n');
        } catch (IOException e) {
            throw new IllegalStateException("Could not read database migration", e);
        }
        for (String statement : script.toString().split(";")) {
            String sql = statement.trim();
            if (!sql.isEmpty()) db.execSQL(sql);
        }
    }

    public synchronized List<ListRow> lists() {
        List<ListRow> rows = new ArrayList<>();
        String sql = "SELECT l.id,l.name,l.created_at," +
                "(SELECT COUNT(*) FROM items i WHERE i.list_id=l.id)," +
                "(SELECT COUNT(*) FROM images g WHERE g.list_id=l.id) " +
                "FROM shopping_lists l ORDER BY l.updated_at DESC,l.id DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) rows.add(new ListRow(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3), c.getInt(4)));
        }
        return rows;
    }

    public synchronized long createList(String name) {
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("name", name == null || name.trim().isEmpty() ? ShoppingListModel.nameAt(now) : name.trim());
        values.put("created_at", now);
        values.put("updated_at", now);
        return getWritableDatabase().insertOrThrow("shopping_lists", null, values);
    }

    public synchronized boolean containsList(long listId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT 1 FROM shopping_lists WHERE id=?", new String[]{String.valueOf(listId)})) {
            return c.moveToFirst();
        }
    }

    public synchronized List<Item> items() { return items(1L); }

    public synchronized List<Item> items(long listId) {
        List<Item> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,name,quantity,store,category,sort_order,done FROM items WHERE list_id=? ORDER BY sort_order,id",
                new String[]{String.valueOf(listId)})) {
            while (c.moveToNext()) rows.add(new Item(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5), c.getInt(6) == 1));
        }
        return rows;
    }

    public synchronized void appendItems(long listId, List<LocalParser.ItemDraft> items) {
        if (items == null || items.isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            requireList(db, listId);
            int lastOrder = lastSortOrder(db, "items", listId);
            int offset = 0;
            for (LocalParser.ItemDraft item : items) {
                insertItem(db, listId, item, ShoppingListModel.nextSortOrder(lastOrder, offset++));
            }
            updateListTime(db, listId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void replaceItems(List<LocalParser.ItemDraft> items) { replaceItems(1L, items); }

    public synchronized void replaceItems(long listId, List<LocalParser.ItemDraft> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            requireList(db, listId);
            db.delete("items", "list_id=?", new String[]{String.valueOf(listId)});
            int order = 0;
            if (items != null) for (LocalParser.ItemDraft item : items) insertItem(db, listId, item, order++);
            updateListTime(db, listId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void addItem(String name) { addItem(1L, name); }

    public synchronized void addItem(long listId, String name) {
        List<LocalParser.ItemDraft> item = new ArrayList<>();
        item.add(new LocalParser.ItemDraft(name, "", "未指定", "其他", 0));
        appendItems(listId, item);
    }

    public synchronized void updateItem(long id, String name, String quantity, String store) {
        updateItem(1L, id, name, quantity, store);
    }

    public synchronized void updateItem(long listId, long id, String name, String quantity, String store) {
        ContentValues v = new ContentValues(); v.put("name", name); v.put("quantity", quantity); v.put("store", store);
        SQLiteDatabase db = getWritableDatabase();
        db.update("items", v, "id=? AND list_id=?", new String[]{String.valueOf(id), String.valueOf(listId)});
        updateListTime(db, listId);
    }

    public synchronized void toggleItem(long id, boolean done) { toggleItem(1L, id, done); }
    public synchronized void toggleItem(long listId, long id, boolean done) { setDone("items", listId, id, done); }
    public synchronized void deleteItem(long id) { deleteItem(1L, id); }
    public synchronized void deleteItem(long listId, long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("items", "id=? AND list_id=?", new String[]{String.valueOf(id), String.valueOf(listId)});
        updateListTime(db, listId);
    }

    public synchronized List<ImageRow> images() { return images(1L); }

    public synchronized List<ImageRow> images(long listId) {
        List<ImageRow> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,path,sort_order,done FROM images WHERE list_id=? ORDER BY sort_order,id",
                new String[]{String.valueOf(listId)})) {
            while (c.moveToNext()) rows.add(new ImageRow(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3) == 1));
        }
        return rows;
    }

    public synchronized long addImage(String path) { return addImage(1L, path); }

    public synchronized long addImage(long listId, String path) {
        SQLiteDatabase db = getWritableDatabase();
        requireList(db, listId);
        ContentValues v = new ContentValues();
        v.put("path", path); v.put("sort_order", ShoppingListModel.nextSortOrder(lastSortOrder(db, "images", listId), 0)); v.put("done", 0); v.put("list_id", listId);
        long id = db.insertOrThrow("images", null, v);
        updateListTime(db, listId);
        return id;
    }

    public synchronized void toggleImage(long id, boolean done) { toggleImage(1L, id, done); }
    public synchronized void toggleImage(long listId, long id, boolean done) { setDone("images", listId, id, done); }
    public synchronized void deleteImage(long id) { deleteImage(1L, id); }
    public synchronized void deleteImage(long listId, long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("images", "id=? AND list_id=?", new String[]{String.valueOf(id), String.valueOf(listId)});
        updateListTime(db, listId);
    }

    public synchronized List<String> completedImagePaths() { return completedImagePaths(1L); }

    public synchronized List<String> completedImagePaths(long listId) {
        List<String> paths = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT path FROM images WHERE done=1 AND list_id=?", new String[]{String.valueOf(listId)})) {
            while (c.moveToNext()) paths.add(c.getString(0));
        }
        return paths;
    }

    public synchronized void clearCompleted() { clearCompleted(1L); }

    public synchronized void clearCompleted(long listId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("items", "done=1 AND list_id=?", new String[]{String.valueOf(listId)});
            db.delete("images", "done=1 AND list_id=?", new String[]{String.valueOf(listId)});
            updateListTime(db, listId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized List<String> clearList(long listId) {
        SQLiteDatabase db = getWritableDatabase();
        List<String> paths = new ArrayList<>();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT path FROM images WHERE list_id=?", new String[]{String.valueOf(listId)})) {
                while (c.moveToNext()) paths.add(c.getString(0));
            }
            db.delete("items", "list_id=?", new String[]{String.valueOf(listId)});
            db.delete("images", "list_id=?", new String[]{String.valueOf(listId)});
            updateListTime(db, listId);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return paths;
    }

    public synchronized List<String> deleteList(long listId) {
        SQLiteDatabase db = getWritableDatabase();
        List<String> paths = new ArrayList<>();
        db.beginTransaction();
        try {
            try (Cursor c = db.rawQuery("SELECT path FROM images WHERE list_id=?", new String[]{String.valueOf(listId)})) {
                while (c.moveToNext()) paths.add(c.getString(0));
            }
            db.delete("items", "list_id=?", new String[]{String.valueOf(listId)});
            db.delete("images", "list_id=?", new String[]{String.valueOf(listId)});
            db.delete("shopping_lists", "id=?", new String[]{String.valueOf(listId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return paths;
    }

    private void insertItem(SQLiteDatabase db, long listId, LocalParser.ItemDraft item, int order) {
        ContentValues v = new ContentValues();
        v.put("name", item.name); v.put("quantity", item.quantity); v.put("store", item.store);
        v.put("category", item.category); v.put("sort_order", order); v.put("done", 0); v.put("list_id", listId);
        db.insertOrThrow("items", null, v);
    }

    private int lastSortOrder(SQLiteDatabase db, String table, long listId) {
        if (!"items".equals(table) && !"images".equals(table)) throw new IllegalArgumentException("Unsupported table");
        try (Cursor c = db.rawQuery("SELECT COALESCE(MAX(sort_order),-1) FROM " + table + " WHERE list_id=?", new String[]{String.valueOf(listId)})) {
            return c.moveToFirst() ? c.getInt(0) : -1;
        }
    }

    private void requireList(SQLiteDatabase db, long listId) {
        try (Cursor c = db.rawQuery("SELECT 1 FROM shopping_lists WHERE id=?", new String[]{String.valueOf(listId)})) {
            if (!c.moveToFirst()) throw new IllegalArgumentException("Shopping list does not exist: " + listId);
        }
    }

    private void updateListTime(SQLiteDatabase db, long listId) {
        ContentValues values = new ContentValues(); values.put("updated_at", System.currentTimeMillis());
        db.update("shopping_lists", values, "id=?", new String[]{String.valueOf(listId)});
    }

    private void setDone(String table, long listId, long id, boolean done) {
        if (!"items".equals(table) && !"images".equals(table)) throw new IllegalArgumentException("Unsupported table");
        ContentValues v = new ContentValues(); v.put("done", done ? 1 : 0);
        SQLiteDatabase db = getWritableDatabase();
        db.update(table, v, "id=? AND list_id=?", new String[]{String.valueOf(id), String.valueOf(listId)});
        updateListTime(db, listId);
    }

    public static final class Item {
        public final long id; public final String name, quantity, store, category; public final int order; public final boolean done;
        Item(long id, String name, String quantity, String store, String category, int order, boolean done) {
            this.id=id; this.name=name; this.quantity=quantity; this.store=store; this.category=category; this.order=order; this.done=done;
        }
    }
    public static final class ListRow {
        public final long id; public final String name; public final long createdAt; public final int itemCount, imageCount;
        ListRow(long id, String name, long createdAt, int itemCount, int imageCount) {
            this.id=id; this.name=name; this.createdAt=createdAt; this.itemCount=itemCount; this.imageCount=imageCount;
        }
    }
    public static final class ImageRow {
        public final long id; public final String path; public final int order; public final boolean done;
        ImageRow(long id, String path, int order, boolean done) { this.id=id; this.path=path; this.order=order; this.done=done; }
    }
}
