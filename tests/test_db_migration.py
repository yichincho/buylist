import sqlite3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
V1_SCHEMA = """
CREATE TABLE items(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    quantity TEXT NOT NULL,
    store TEXT NOT NULL,
    category TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    done INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE images(
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    path TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    done INTEGER NOT NULL DEFAULT 0
);
"""


class ShoppingDbMigrationTest(unittest.TestCase):
    def test_v1_rows_survive_migration(self):
        conn = sqlite3.connect(":memory:")
        try:
            conn.executescript(V1_SCHEMA)
            conn.execute(
                "INSERT INTO items VALUES (1,'牛奶','2瓶','全聯','食品飲料',0,1)"
            )
            conn.execute("INSERT INTO images VALUES (1,'/private/ref.img',0,1)")

            migration_path = ROOT / "app/src/main/res/raw/migrate_v1_v2.sql"
            self.assertTrue(migration_path.exists(), "migration resource does not exist")
            conn.executescript(migration_path.read_text(encoding="utf-8"))

            self.assertEqual(
                conn.execute(
                    "SELECT id,name,quantity,store,category,sort_order,done,list_id FROM items"
                ).fetchone(),
                (1, "牛奶", "2瓶", "全聯", "食品飲料", 0, 1, 1),
            )
            self.assertEqual(
                conn.execute("SELECT id,path,sort_order,done,list_id FROM images").fetchone(),
                (1, "/private/ref.img", 0, 1, 1),
            )
            self.assertEqual(
                conn.execute("SELECT id,name FROM shopping_lists WHERE id=1").fetchone(),
                (1, "v0.2.1 匯入清單"),
            )
        finally:
            conn.close()


if __name__ == "__main__":
    unittest.main()
