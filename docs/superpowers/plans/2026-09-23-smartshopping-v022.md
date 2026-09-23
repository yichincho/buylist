# SmartShopping v0.2.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax.

**Goal:** Upgrade the existing Java Android app to v0.2.2 with persistent local lists, append-only family inputs, one-tap list actions, and Gemini as the default AI provider.

**Architecture:** Extend the existing SQLiteOpenHelper schema instead of replacing the project with Room. Scope every item/image query and insert by list ID, add a transactional v1-to-v2 migration, and keep each list's draft separately. Keep the existing AI boundary; add a Gemini REST client and provider-specific encrypted keys while retaining DeepSeek.

**Tech Stack:** Java 8 source compatibility, Android API 24–36, SQLiteOpenHelper, Android Keystore AES-GCM, HttpURLConnection, Python 3 sqlite3 migration tests, plain-Java executable tests, custom aapt2/d8 APK build.

**Spec:** docs/superpowers/specs/2026-09-23-smartshopping-multi-list-design.md

## Global Constraints

- Keep package ID tw.yc.smartshopping.
- Preserve the current Java/SQLite project structure; do not rewrite it to Kotlin/Room.
- Keep minSdkVersion 24 and targetSdkVersion 36.
- Preserve existing v0.2.1 items, checked states, reference-image paths, draft, and DeepSeek key.
- Default provider is Gemini; default model is gemini-3.1-flash-lite.
- A user must enter their own provider key; embed no shared key in source or APK.
- Store each provider key encrypted with Android Keystore and never log it or place it in a URL.
- Keep offline local text parsing available without a key or network.
- Keep normal product reference images local; upload an image only for explicit screenshot-recognition input.
- Bump APK version from code 3/name 0.2.1 to code 4/name 0.2.2.

## Review Focus

- Existing rows and image file paths survive migration; cover with the migration test in Task 1.
- Two lists never show each other's entries or images; verify list-scoped reads in Task 2 and the phone checklist in Task 6.
- A background image/AI task finishing after a list switch writes to the list active when the task began; test target-list capture in Task 3.
- Clearing text leaves saved content intact, while clearing a list removes only that list's content; verify action semantics in Task 3 and the phone checklist in Task 6.
- Existing DeepSeek credentials remain usable after Gemini becomes default; verify legacy-key read and provider defaults in Task 5.

## File Map

- app/src/main/java/tw/yc/smartshopping/ShoppingDb.java — schema version 2, migration runner, list CRUD, list-scoped item/image CRUD.
- app/src/main/java/tw/yc/smartshopping/ShoppingListModel.java — pure-Java timestamp naming and sort-order helper used by ShoppingDb.
- app/src/main/java/tw/yc/smartshopping/ShoppingListWorkflow.java — platform-free list actions used by MainActivity and tested with a fake Store.
- app/src/main/res/raw/migrate_v1_v2.sql — SQLite migration shared with the host-side test.
- app/src/main/java/tw/yc/smartshopping/MainActivity.java — selected-list UI, per-list drafts, append/clear/new/delete actions, and captured list IDs for asynchronous work.
- app/src/main/java/tw/yc/smartshopping/GeminiClient.java — direct Gemini REST requests.
- app/src/main/java/tw/yc/smartshopping/GeminiResponseParser.java — response validation.
- app/src/main/java/tw/yc/smartshopping/AiProvider.java — provider constants and default resolution.
- app/src/main/java/tw/yc/smartshopping/SecureKeyStore.java — separate encrypted credentials with legacy DeepSeek compatibility.
- app/src/main/java/tw/yc/smartshopping/SettingsActivity.java — provider selector, provider-specific key entry, save/clear/test.
- app/src/main/res/values/ids.xml and strings.xml — IDs and visible labels.
- scripts/build-apk.sh — version bump, expanded JVM test list, Gemini-key scan.
- tests/test_db_migration.py — host-side migration regression test.
- tests/ShoppingListModelTest.java and tests/ShoppingListWorkflowTest.java — executable JVM list workflow tests.
- tests/GeminiResponseParserTest.java, tests/AiProviderTest.java and tests/GeminiClientTest.java — executable JVM tests.
- docs/testing/xiaomi-mix2-checklist.md — v0.2.2 list, migration, key and clear-all acceptance steps.
- README.md — explain list history, provider setup, and per-user API keys.

---

### Task 1: Pin and wire the v1-to-v2 SQLite migration

**Files:** Create tests/test_db_migration.py and app/src/main/res/raw/migrate_v1_v2.sql. Modify app/src/main/java/tw/yc/smartshopping/ShoppingDb.java.

**Interfaces:** The migration resource contains deterministic SQLite statements. ShoppingDb.onUpgrade loads and executes the same resource tested by Python.

- [ ] **Step 1: Write the failing migration test**

Create a v1 in-memory database with the original items and images columns, insert a checked item and an image path, execute the migration resource, and assert that both rows get list ID 1 while all old values remain.

~~~python
def test_v1_rows_survive_migration():
    conn = sqlite3.connect(":memory:")
    conn.executescript(V1_SCHEMA)
    conn.execute("INSERT INTO items VALUES (1,'牛奶','2瓶','全聯','食品飲料',0,1)")
    conn.execute("INSERT INTO images VALUES (1,'/private/ref.img',0,1)")
    migration = Path("app/src/main/res/raw/migrate_v1_v2.sql").read_text()
    conn.executescript(migration)
    assert conn.execute("SELECT name,quantity,done,list_id FROM items").fetchone() == ("牛奶","2瓶",1,1)
    assert conn.execute("SELECT path,done,list_id FROM images").fetchone() == ("/private/ref.img",1,1)
    assert conn.execute("SELECT name FROM shopping_lists WHERE id=1").fetchone() == ("v0.2.1 匯入清單",)
~~~

Define V1_SCHEMA from the current v0.2.1 CREATE TABLE statements in ShoppingDb.onCreate.

- [ ] **Step 2: Run the migration test and confirm it fails**

Run: python3 tests/test_db_migration.py
Expected: FAIL because migrate_v1_v2.sql does not exist.

- [ ] **Step 3: Add the shared migration SQL**

Create shopping_lists(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL), insert ID 1 named v0.2.1 匯入清單, add list_id INTEGER NOT NULL DEFAULT 1 to items and images, and add indexes on list_id,sort_order,id.

- [ ] **Step 4: Run the migration test and verify preserved values**

Run: python3 tests/test_db_migration.py
Expected: PASS with the checked item, image path and completion flags preserved.

- [ ] **Step 5: Wire Android database version 2**

Set ShoppingDb version to 2. In onCreate create the v2 tables and a first 採買清單. In onUpgrade execute each nonempty semicolon-delimited statement from R.raw.migrate_v1_v2 inside SQLiteOpenHelper's upgrade transaction. Do not delete or rewrite image files.

- [ ] **Step 6: Build and commit the migration slice**

Run: ANDROID_SDK_ROOT=/workspace/scratch/c5b5f38223c8/android-sdk bash scripts/build-apk.sh
Expected: Java compilation and APK assembly succeed. Commit the migration test, SQL resource and database version change.

### Task 2: Add list-scoped database operations

**Files:** Modify ShoppingDb.java and scripts/build-apk.sh. Create ShoppingListModel.java, ShoppingListWorkflow.java, tests/ShoppingListModelTest.java and tests/ShoppingListWorkflowTest.java.

**Interfaces:**
- ListRow exposes id, name, createdAt, itemCount and imageCount.
- ShoppingDb.lists() returns rows ordered by updated_at DESC,id DESC.
- ShoppingDb.createList(String name) returns the new list ID.
- ShoppingDb.items(long listId) and images(long listId) return only rows in that list.
- ShoppingDb.appendItems(long listId,List<LocalParser.ItemDraft> items) inserts without deleting old rows.
- ShoppingDb.addImage(long listId,String path) assigns the image to the selected list.
- ShoppingDb.clearList(long listId) transactionally removes that list's rows and returns image paths.
- ShoppingDb.clearCompleted(long listId) removes completed rows only from that list.
- ShoppingDb.deleteList(long listId) removes one list and returns its image paths; callers delete files only after the DB transaction succeeds.
- ShoppingListWorkflow is constructed with Store and initialListId; activeListId() returns its current selection.
- ShoppingListWorkflow.Store exposes appendItems(listId,items), createList(name), clearList(listId), deleteList(listId) and containsList(listId).
- ShoppingListWorkflow.addText(text) parses and appends nonempty input to its active list; saveAndStartNew(timestampMillis) preserves it and selects a new one; clearCurrentList() returns only current-list image paths; deleteList(listId,timestampMillis) selects a new list if deleting the current one.

- [ ] **Step 1: Write failing tests for list naming and duplicate preservation**

Add plain-Java tests for ShoppingListModel.nameAt(long timestampMillis) and nextSortOrder(int existingCount,int offset), plus ShoppingListWorkflow.addText(), saveAndStartNew(), clearCurrentList() and deleteList() using a fake Store. Assert input appends after an existing item, duplicate names stay separate, save-new preserves the old list and selects an empty one, clear affects only the active list, and deleting the active list selects a fresh one. Run through the javac/java harness; confirm missing classes fail compilation.

- [ ] **Step 2: Implement the pure-Java list model**

Add ShoppingListModel with nameAt(long timestampMillis) and nextSortOrder(int existingCount,int offset). Add ShoppingListWorkflow with the Store interface and tested operations, including activeListId and list selection; keep both classes free of Android dependencies. Use nextSortOrder in ShoppingDb.appendItems while inserting rows.

- [ ] **Step 3: Run the model test**

Run ShoppingListModelTest and ShoppingListWorkflowTest with the plain-Java harness. Expected: PASS for timestamp formatting, ordered append, duplicate preservation, save-new and clear-current isolation.

- [ ] **Step 4: Add list-scoped SQLite CRUD**

Filter all item/image reads by list_id. Pass the selected ID into manual item insert, AI append, image import, completion clearing, and deletion. Use a transaction for clearList and deleteList. Return image paths before deleting rows so the caller removes files only after commit.

- [ ] **Step 5: Run migration, model and APK checks**

Run: python3 tests/test_db_migration.py and ANDROID_SDK_ROOT=/workspace/scratch/c5b5f38223c8/android-sdk bash scripts/build-apk.sh. Expected: all tests pass and APK compilation succeeds.

### Task 3: Implement current-list UI and safe list actions

**Files:** Modify MainActivity.java, app/src/main/res/values/ids.xml, app/src/main/res/values/strings.xml and docs/testing/xiaomi-mix2-checklist.md.

**Interfaces:**
- MainActivity owns ShoppingListWorkflow and persists its activeListId; it loads all visible items/images using that ID.
- Draft preference key is draft:<listId>; read the v0.2.1 global draft once for the imported list.
- Active-list preference key is active_list_id; if missing or invalid, use the first existing list.
- Every background operation captures its target list ID before starting.

- [ ] **Step 1: Add failing UI acceptance cases before changing handlers**

Add cases for clear-text, append text, list isolation, save-and-new, clear-current-list and delete-history-list to the MIX 2 checklist. Record the current failure state before changing handlers.

- [ ] **Step 2: Render the selected list and history picker**

Show the current name under the app title. Add a history action that lists name, item count and image count and opens a selected list. Switching saves the old draft and loads the selected list's draft, items and images.

- [ ] **Step 3: Add append and clear-text actions**

Add 加入清單; call ShoppingListWorkflow.addText(draftText), then clear the field after successful insertion. Add 清除文字 that empties only the text field and saves an empty per-list draft.

- [ ] **Step 4: Change AI and screenshot results to append**

Replace destructive replaceItems calls with appendItems(targetListId,result.items). Capture targetListId before starting background work. Refresh the screen only if that ID remains active when the callback returns. Keep the draft on a failed request; clear it after successful or local-fallback append.

- [ ] **Step 5: Add save-new, clear-current and delete-history actions**

儲存並開新清單 calls ShoppingListWorkflow.saveAndStartNew(now), persists its ID, clears the draft and renders empty items/images. 清除整張清單 asks once, calls clearCurrentList(), then deletes returned image files. History deletion asks for confirmation; deleting the active list creates and selects a new empty list.

- [ ] **Step 6: Run tests and rebuild**

Run the full host test suite and rebuild the APK. Manually execute the MIX 2 checklist when a device is available; leave device results marked pending until actually run.

### Task 4: Add Gemini provider selection and response parsing

**Files:** Create AiProvider.java, GeminiResponseParser.java, tests/AiProviderTest.java and tests/GeminiResponseParserTest.java. Modify scripts/build-apk.sh.

**Interfaces:**
- AiProvider.resolve(String value) returns Gemini for empty/unknown values and DeepSeek only for the exact DeepSeek identifier.
- GeminiResponseParser.parse(String body,String originalText) returns a validated result using the shared item-draft result type. Missing candidates, empty text and malformed JSON return LocalParser output from originalText without throwing into MainActivity.
- Default model is gemini-3.1-flash-lite.

- [ ] **Step 1: Write failing provider and parser tests**

Test default Gemini, explicit DeepSeek, valid candidate text containing JSON, missing candidate parts, empty candidates and malformed JSON.

- [ ] **Step 2: Run the tests and confirm they fail**

Run the plain-Java harness for AiProviderTest and GeminiResponseParserTest. Expected: compilation fails because the classes are missing.

- [ ] **Step 3: Implement provider resolution and response parsing**

Parse candidate content parts in order and concatenate text parts. Validate an items array, trim names, supply 未指定/其他 defaults, discard blank names and use LocalParser.parse(originalText) on an invalid response.

- [ ] **Step 4: Run provider/parser tests**

Run the expanded plain-Java harness. Expected: all valid, empty and malformed response tests pass without network access.

### Task 5: Implement Gemini REST, encrypted credentials and settings

**Files:** Create GeminiClient.java and tests/GeminiClientTest.java. Modify SecureKeyStore.java, SettingsActivity.java and scripts/build-apk.sh.

**Interfaces:**
- GeminiClient() uses Google's production endpoint; GeminiClient(String baseUrl) accepts an injected base URL for the local HTTP test. organize(String key,String text) sends text and requests JSON output.
- GeminiClient.organizeImage(String key,byte[] jpeg) sends the explicit screenshot as inlineData.
- GeminiClient.test(String key) performs a one-item validation request.
- Both organize methods return the existing DeepSeekClient.Result structure.
- SecureKeyStore.getForProvider(String provider), saveForProvider(String provider,String key) and clearForProvider(String provider) isolate keys; get() continues reading the v0.2.1 DeepSeek key.

- [ ] **Step 1: Write failing local HTTP tests**

Start a local JDK HttpServer in GeminiClientTest and construct GeminiClient with its base URL. Assert POST path /v1beta/models/gemini-3.1-flash-lite:generateContent, x-goog-api-key header, JSON output configuration, valid-response parsing, and inlineData.mimeType plus Base64 image data only in the image request.

- [ ] **Step 2: Run the HTTP test and confirm failure**

Run GeminiClientTest through the plain-Java harness. Expected: compilation fails because GeminiClient does not exist.

- [ ] **Step 3: Implement the REST client**

Use HttpURLConnection with bounded connect/read timeouts, key in x-goog-api-key rather than URL, and no request/response logging. Map 401/403, 429, timeout, other non-2xx and malformed output to readable fallback messages.

- [ ] **Step 4: Run Gemini HTTP tests**

Expected: the local server observes the model path, key header, JSON generation config, text part and image inlineData; valid JSON becomes item drafts.

- [ ] **Step 5: Add provider-specific encrypted key slots**

Encrypt each provider key separately with AES-GCM. Read legacy iv/ciphertext as DeepSeek if no new DeepSeek slot exists. Clearing one provider must leave the other untouched. Add manual Settings checks for Gemini save/restart/clear and DeepSeek key migration because Android Keystore cannot be exercised by the host JVM harness.

- [ ] **Step 6: Update settings and active-provider dispatch**

Make Gemini the initial provider, show the matching Key hint/model, and test the selected provider. Save/clear only the selected provider. Keep DeepSeek selectable and its old encrypted credential readable.

### Task 6: Version, documentation, verification and packaging

**Files:** Modify scripts/build-apk.sh, README.md and docs/testing/xiaomi-mix2-checklist.md.

- [ ] **Step 1: Bump metadata and update documentation**

Set version code 4 and version name 0.2.2. Document list history, append/clear actions, Gemini key setup, DeepSeek choice, offline parsing and per-user keys.

- [ ] **Step 2: Expand the secret scan**

Scan source and APK for DeepSeek-style sk- keys and Google AI Studio-style AIza keys. Print success only when no matching key is found.

- [ ] **Step 3: Run complete automated verification**

Run:

~~~bash
python3 -m unittest discover -s tests -v
ANDROID_SDK_ROOT=/workspace/scratch/c5b5f38223c8/android-sdk bash scripts/build-apk.sh
~~~

Expected: Python/JVM tests pass, aapt2/d8/APK signing verification succeeds, and the secret scan reports no embedded key.

- [ ] **Step 4: Inspect metadata and package deliverables**

Use aapt dump badging and apksigner verify to confirm package tw.yc.smartshopping, version 0.2.2, code 4, and a valid signed debug APK. Copy the verified APK to dist/SmartShopping-v0.2.2.apk and package the full source as dist/SmartShopping-source-v0.2.2.zip.

- [ ] **Step 5: Finalize the phone checklist**

Include migration from 0.2.1, text append/clear, mixed image/text input, save-and-new, list switching, clear confirmation, history deletion, Gemini default, valid/invalid Gemini Key, retained DeepSeek Key and offline fallback. Mark physical execution pending unless it was run on the Xiaomi MIX 2.
