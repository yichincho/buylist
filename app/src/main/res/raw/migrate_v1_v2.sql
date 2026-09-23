CREATE TABLE shopping_lists (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
INSERT INTO shopping_lists (id, name, created_at, updated_at)
VALUES (1, 'v0.2.1 匯入清單', 0, 0);
ALTER TABLE items ADD COLUMN list_id INTEGER NOT NULL DEFAULT 1;
ALTER TABLE images ADD COLUMN list_id INTEGER NOT NULL DEFAULT 1;
CREATE INDEX IF NOT EXISTS idx_items_list_sort ON items (list_id, sort_order, id);
CREATE INDEX IF NOT EXISTS idx_images_list_sort ON images (list_id, sort_order, id);
