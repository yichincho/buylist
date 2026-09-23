package tw.yc.smartshopping;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQ_IMAGES = 300;
    private static final int REQ_VOICE = 301;
    private static final int REQ_MIC = 400;
    private static final String PREF_ACTIVE_LIST_ID = "active_list_id";
    private static final String PREF_LEGACY_DRAFT_MIGRATED = "legacy_draft_migrated";

    private EditText draft;
    private LinearLayout listContainer;
    private GridLayout imageGrid;
    private TextView status;
    private TextView currentListLabel;
    private ShoppingDb db;
    private SecureKeyStore keyStore;
    private SharedPreferences preferences;
    private ShoppingListWorkflow listWorkflow;
    private boolean suppressDraftSave;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new ShoppingDb(this);
        keyStore = new SecureKeyStore(this);
        preferences = getPreferences(MODE_PRIVATE);
        initializeLists();
        buildUi();
        handleShare(getIntent());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (listContainer != null) render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private void initializeLists() {
        List<ShoppingDb.ListRow> rows = db.lists();
        if (rows.isEmpty()) {
            db.createList(getString(R.string.default_list_name));
            rows = db.lists();
        }
        if (!preferences.getBoolean(PREF_LEGACY_DRAFT_MIGRATED, false)) {
            String oldDraft = preferences.getString("draft", null);
            SharedPreferences.Editor editor = preferences.edit().putBoolean(PREF_LEGACY_DRAFT_MIGRATED, true).remove("draft");
            if (oldDraft != null) editor.putString(draftKey(1L), oldDraft);
            editor.apply();
        }

        long savedId = preferences.getLong(PREF_ACTIVE_LIST_ID, -1L);
        if (!db.containsList(savedId)) savedId = rows.get(0).id;
        listWorkflow = new ShoppingListWorkflow(new ShoppingListWorkflow.Store() {
            @Override public void appendItems(long listId, List<LocalParser.ItemDraft> items) {
                db.appendItems(listId, items);
            }
            @Override public long createList(String name) { return db.createList(name); }
            @Override public List<String> clearList(long listId) { return db.clearList(listId); }
            @Override public List<String> deleteList(long listId) { return db.deleteList(listId); }
            @Override public boolean containsList(long listId) { return db.containsList(listId); }
        }, savedId);
        preferences.edit().putLong(PREF_ACTIVE_LIST_ID, savedId).apply();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(12), dp(16), dp(40));
        scroll.addView(root);

        LinearLayout header = row();
        LinearLayout heading = column();
        heading.addView(title(getString(R.string.app_name), 27));
        currentListLabel = label("");
        currentListLabel.setId(R.id.currentListTitle);
        currentListLabel.setTextColor(Color.rgb(60, 95, 62));
        heading.addView(currentListLabel);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button history = button(getString(R.string.list_history));
        history.setId(R.id.listHistoryButton);
        Button settings = button("設定 ⚙");
        header.addView(history);
        header.addView(settings);
        root.addView(header);
        history.setOnClickListener(v -> showHistory());
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        draft = new EditText(this);
        draft.setId(R.id.inputDraft);
        draft.setHint("貼上或輸入要買的東西…\n例如：好市多牛奶、全聯雞蛋、衛生紙 2 包");
        draft.setMinLines(4);
        draft.setGravity(Gravity.TOP);
        draft.setText(preferences.getString(draftKey(activeListId()), ""));
        root.addView(draft, matchWrap());
        draft.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressDraftSave) saveDraft(activeListId(), s.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        LinearLayout inputActions = row();
        Button paste = button("貼上文字");
        Button voice = button("🎤 語音輸入");
        inputActions.addView(paste, new LinearLayout.LayoutParams(0, -2, 1));
        inputActions.addView(voice, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(inputActions);
        paste.setOnClickListener(v -> pasteText());
        voice.setOnClickListener(v -> requestVoice());

        LinearLayout draftActions = row();
        Button addDraft = button(getString(R.string.add_text_to_list));
        addDraft.setId(R.id.addDraftButton);
        Button clearText = button(getString(R.string.clear_text));
        clearText.setId(R.id.clearDraftButton);
        draftActions.addView(addDraft, new LinearLayout.LayoutParams(0, -2, 1));
        draftActions.addView(clearText, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(draftActions);
        addDraft.setOnClickListener(v -> addDraftToList());
        clearText.setOnClickListener(v -> clearDraftText());

        Button organize = button(getString(R.string.ai_organize_add));
        organize.setId(R.id.organizeButton);
        organize.setTextSize(18);
        root.addView(organize, matchWrap());
        organize.setOnClickListener(v -> organize(organize));
        status = label("");
        root.addView(status, matchWrap());

        root.addView(title("購物項目", 22));
        listContainer = column();
        listContainer.setId(R.id.shoppingListContainer);
        root.addView(listContainer, matchWrap());

        root.addView(title("商品圖片（不必對應品項）", 22));
        root.addView(label("可從 LINE 分享、相簿選擇或嘗試貼上剪貼簿圖片。"));
        imageGrid = new GridLayout(this);
        imageGrid.setId(R.id.imageGrid);
        imageGrid.setColumnCount(2);
        root.addView(imageGrid, matchWrap());

        LinearLayout imageActions = row();
        Button gallery = button("＋ 相簿圖片");
        Button clipImage = button("貼上圖片");
        imageActions.addView(gallery, new LinearLayout.LayoutParams(0, -2, 1));
        imageActions.addView(clipImage, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(imageActions);
        gallery.setOnClickListener(v -> pickImages());
        clipImage.setOnClickListener(v -> pasteImage());

        LinearLayout itemActions = row();
        Button addItem = button("＋ 新增品項");
        Button clearCompleted = button("清除已完成");
        itemActions.addView(addItem, new LinearLayout.LayoutParams(0, -2, 1));
        itemActions.addView(clearCompleted, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(itemActions);
        addItem.setOnClickListener(v -> addItemDialog());
        clearCompleted.setOnClickListener(v -> confirmClearCompleted());

        LinearLayout listActions = row();
        Button saveAndNew = button(getString(R.string.save_and_new_list));
        saveAndNew.setId(R.id.saveAndNewListButton);
        Button clearList = button(getString(R.string.clear_whole_list));
        clearList.setId(R.id.clearWholeListButton);
        listActions.addView(saveAndNew, new LinearLayout.LayoutParams(0, -2, 1));
        listActions.addView(clearList, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(listActions);
        saveAndNew.setOnClickListener(v -> saveAndStartNewList());
        clearList.setOnClickListener(v -> confirmClearCurrentList());

        updateCurrentListLabel();
        setContentView(scroll);
    }

    private void addDraftToList() {
        String text = draft.getText().toString();
        if (text.trim().isEmpty()) {
            toast("請先輸入購物內容");
            return;
        }
        try {
            int count = listWorkflow.addText(text);
            if (count == 0) {
                toast("沒有可加入的品項");
                return;
            }
            clearDraftForList(activeListId());
            status.setText("已加入 " + count + " 項");
            render();
        } catch (RuntimeException error) {
            toast("加入失敗，文字仍保留在輸入框");
        }
    }

    private void clearDraftText() {
        clearDraftForList(activeListId());
        status.setText("已清除輸入文字；清單內容保留");
    }

    private void organize(Button button) {
        String source = draft.getText().toString();
        if (source.trim().isEmpty()) {
            toast("請先輸入購物內容");
            return;
        }
        final long targetListId = activeListId();
        final AiProvider targetProvider = selectedProvider();
        button.setEnabled(false);
        status.setText("正在整理…");
        executor.execute(() -> {
            String key = keyStore.getForProvider(targetProvider.id());
            DeepSeekClient.Result result = targetProvider == AiProvider.GEMINI
                    ? new GeminiClient().organize(key, source)
                    : DeepSeekClient.organize(key, source);
            String saveError = null;
            boolean appendSucceeded = false;
            if (!result.items.isEmpty()) {
                try {
                    db.appendItems(targetListId, result.items);
                    appendSucceeded = true;
                } catch (RuntimeException error) {
                    saveError = "加入清單失敗，輸入文字已保留";
                }
                if (appendSucceeded) clearSavedDraftIfUnchanged(targetListId, source);
            }
            final String message = saveError == null ? result.message : saveError;
            final boolean saved = appendSucceeded;
            runOnUiThread(() -> {
                button.setEnabled(true);
                if (listWorkflow.activeListId() == targetListId) {
                    if (ShoppingListModel.shouldClearDraftAfterAppend(saved, source, draft.getText().toString())) {
                        clearDraftForList(targetListId);
                    }
                    status.setText(message);
                    render();
                }
            });
        });
    }

    private void saveAndStartNewList() {
        saveDraft(activeListId(), draft.getText().toString());
        long newListId = listWorkflow.saveAndStartNew(System.currentTimeMillis());
        preferences.edit().putLong(PREF_ACTIVE_LIST_ID, newListId).putString(draftKey(newListId), "").apply();
        setDraftText("");
        status.setText("已儲存舊清單並開啟新清單");
        render();
    }

    private void confirmClearCurrentList() {
        long targetListId = activeListId();
        String name = currentListName();
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_whole_list))
                .setMessage("確定清除「" + name + "」中的全部品項、圖片和輸入文字？")
                .setPositiveButton("清除", (dialog, which) -> {
                    List<String> paths = listWorkflow.clearCurrentList();
                    deleteFiles(paths);
                    clearDraftForList(targetListId);
                    status.setText("已清空目前清單");
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHistory() {
        List<ShoppingDb.ListRow> rows = db.lists();
        if (rows.isEmpty()) {
            toast("目前沒有清單");
            return;
        }
        String[] labels = new String[rows.size()];
        int selectedIndex = 0;
        for (int i = 0; i < rows.size(); i++) {
            ShoppingDb.ListRow row = rows.get(i);
            labels[i] = row.name + "（" + row.itemCount + " 項、" + row.imageCount + " 張圖）";
            if (row.id == activeListId()) selectedIndex = i;
        }
        final int[] selected = {selectedIndex};
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.list_history))
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> selected[0] = which)
                .setPositiveButton("開啟", (dialog, which) -> switchList(rows.get(selected[0]).id))
                .setNeutralButton("刪除這張清單", (dialog, which) -> confirmDeleteList(rows.get(selected[0])))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDeleteList(ShoppingDb.ListRow list) {
        new AlertDialog.Builder(this)
                .setTitle("刪除清單")
                .setMessage("確定刪除「" + list.name + "」及其品項、圖片？此操作無法復原。")
                .setPositiveButton("刪除", (dialog, which) -> {
                    boolean deletingActive = list.id == activeListId();
                    List<String> paths = listWorkflow.deleteList(list.id, System.currentTimeMillis());
                    deleteFiles(paths);
                    preferences.edit().remove(draftKey(list.id)).apply();
                    if (deletingActive) {
                        long newListId = activeListId();
                        preferences.edit().putLong(PREF_ACTIVE_LIST_ID, newListId).putString(draftKey(newListId), "").apply();
                        setDraftText("");
                    }
                    status.setText("已刪除清單「" + list.name + "」");
                    render();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void switchList(long listId) {
        if (listId == activeListId()) return;
        saveDraft(activeListId(), draft.getText().toString());
        if (!listWorkflow.switchTo(listId)) {
            toast("找不到這張清單");
            return;
        }
        preferences.edit().putLong(PREF_ACTIVE_LIST_ID, listId).apply();
        setDraftText(preferences.getString(draftKey(listId), ""));
        status.setText("");
        render();
    }

    private void updateCurrentListLabel() {
        if (currentListLabel == null) return;
        currentListLabel.setText(getString(R.string.current_list, currentListName()));
    }

    private String currentListName() {
        long id = activeListId();
        for (ShoppingDb.ListRow list : db.lists()) if (list.id == id) return list.name;
        return getString(R.string.default_list_name);
    }

    private long activeListId() { return listWorkflow.activeListId(); }

    private AiProvider selectedProvider() {
        return AiProvider.resolve(getSharedPreferences("shopping_settings", MODE_PRIVATE).getString("ai_provider", null));
    }

    private String draftKey(long listId) { return "draft:" + listId; }

    private void saveDraft(long listId, String text) {
        preferences.edit().putString(draftKey(listId), text).apply();
    }

    private void clearDraftForList(long listId) {
        saveDraft(listId, "");
        if (activeListId() == listId && draft != null) setDraftText("");
    }

    private void clearSavedDraftIfUnchanged(long listId, String originalText) {
        if (originalText.equals(preferences.getString(draftKey(listId), ""))) saveDraft(listId, "");
    }

    private void setDraftText(String text) {
        if (draft == null) return;
        suppressDraftSave = true;
        draft.setText(text);
        draft.setSelection(draft.getText().length());
        suppressDraftSave = false;
    }

    private void deleteFiles(List<String> paths) {
        if (paths == null) return;
        for (String path : paths) new File(path).delete();
    }

    private void render() {
        updateCurrentListLabel();
        renderItems();
        renderImages();
    }

    private void renderItems() {
        listContainer.removeAllViews();
        final long listId = activeListId();
        List<ShoppingDb.Item> items = db.items(listId);
        String group = "";
        int number = 1;
        boolean search = getSharedPreferences("shopping_settings", MODE_PRIVATE).getBoolean("image_search_enabled", false);
        for (ShoppingDb.Item item : items) {
            String next = item.store + "｜" + item.category;
            if (!next.equals(group)) {
                TextView heading = title(next, 17);
                heading.setBackgroundColor(Color.rgb(232, 243, 231));
                heading.setPadding(dp(10), dp(8), dp(10), dp(8));
                listContainer.addView(heading, matchWrap());
                group = next;
            }
            LinearLayout row = row();
            row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox check = new CheckBox(this);
            check.setChecked(item.done);
            check.setContentDescription("商品 " + number + " 完成狀態");
            row.addView(check);
            TextView text = label(number + ". " + item.name + (item.quantity.isEmpty() ? "" : "  " + item.quantity));
            text.setTextSize(17);
            text.setAlpha(item.done ? .48f : 1f);
            if (item.done) text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            if (search) {
                Button searchButton = button("找圖");
                row.addView(searchButton);
                searchButton.setOnClickListener(v -> searchImage(item.name));
            }
            check.setOnCheckedChangeListener((button, checked) -> {
                db.toggleItem(listId, item.id, checked);
                if (activeListId() == listId) renderItems();
            });
            text.setOnClickListener(v -> editItemDialog(item, listId));
            text.setOnLongClickListener(v -> {
                confirmDeleteItem(item, listId);
                return true;
            });
            listContainer.addView(row, matchWrap());
            number++;
        }
        if (items.isEmpty()) listContainer.addView(label("尚無品項，可輸入文字後按「加入清單」。"));
    }

    private void renderImages() {
        imageGrid.removeAllViews();
        final long listId = activeListId();
        List<ShoppingDb.ImageRow> images = db.images(listId);
        int width = (getResources().getDisplayMetrics().widthPixels - dp(48)) / 2;
        int number = 1;
        for (ShoppingDb.ImageRow image : images) {
            FrameLayout card = new FrameLayout(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.height = width;
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            ImageView view = new ImageView(this);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(sample(image.path, width));
            view.setAlpha(image.done ? .35f : 1f);
            card.addView(view, new FrameLayout.LayoutParams(-1, -1));
            CheckBox done = new CheckBox(this);
            done.setChecked(image.done);
            done.setContentDescription("商品圖片 " + number + (image.done ? " 已完成" : " 完成狀態"));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END);
            card.addView(done, checkParams);
            done.setOnCheckedChangeListener((button, checked) -> {
                db.toggleImage(listId, image.id, checked);
                if (activeListId() == listId) renderImages();
            });
            view.setOnClickListener(v -> showImage(image.path));
            view.setOnLongClickListener(v -> {
                confirmDeleteImage(image, listId);
                return true;
            });
            imageGrid.addView(card, params);
            number++;
        }
        if (images.isEmpty()) {
            TextView empty = label("尚未加入圖片");
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.columnSpec = GridLayout.spec(0, 2);
            params.width = -1;
            imageGrid.addView(empty, params);
        }
    }

    private void editItemDialog(ShoppingDb.Item item, long listId) {
        LinearLayout form = column();
        form.setPadding(dp(18), 0, dp(18), 0);
        EditText name = input("商品名稱", item.name);
        EditText quantity = input("數量", item.quantity);
        EditText store = input("賣場：好市多／全聯／未指定", item.store);
        form.addView(name);
        form.addView(quantity);
        form.addView(store);
        new AlertDialog.Builder(this).setTitle("編輯品項").setView(form)
                .setPositiveButton("儲存", (dialog, which) -> {
                    if (!name.getText().toString().trim().isEmpty()) {
                        db.updateItem(listId, item.id, name.getText().toString().trim(), quantity.getText().toString().trim(), store.getText().toString().trim());
                        if (activeListId() == listId) renderItems();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void addItemDialog() {
        EditText name = input("商品名稱", "");
        new AlertDialog.Builder(this).setTitle("新增品項").setView(name)
                .setPositiveButton("新增", (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) {
                        db.addItem(activeListId(), value);
                        renderItems();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void confirmDeleteItem(ShoppingDb.Item item, long listId) {
        new AlertDialog.Builder(this).setMessage("刪除「" + item.name + "」？")
                .setPositiveButton("刪除", (dialog, which) -> {
                    db.deleteItem(listId, item.id);
                    if (activeListId() == listId) renderItems();
                }).setNegativeButton("取消", null).show();
    }

    private void confirmDeleteImage(ShoppingDb.ImageRow image, long listId) {
        new AlertDialog.Builder(this).setMessage("刪除這張商品圖片？")
                .setPositiveButton("刪除", (dialog, which) -> {
                    db.deleteImage(listId, image.id);
                    new File(image.path).delete();
                    if (activeListId() == listId) renderImages();
                }).setNegativeButton("取消", null).show();
    }

    private void confirmClearCompleted() {
        long listId = activeListId();
        new AlertDialog.Builder(this).setTitle("清除已完成")
                .setMessage("確定刪除目前清單中所有已勾選的品項與圖片？")
                .setPositiveButton("清除", (dialog, which) -> {
                    List<String> paths = db.completedImagePaths(listId);
                    db.clearCompleted(listId);
                    deleteFiles(paths);
                    render();
                }).setNegativeButton("取消", null).show();
    }

    private void requestVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startVoice();
    }

    private void startVoice() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            startActivityForResult(intent, REQ_VOICE);
        } catch (Exception error) {
            toast("此手機沒有可用的語音辨識服務");
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == REQ_MIC) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startVoice();
            else toast("未授權麥克風，仍可使用文字輸入");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null) return;
        if (request == REQ_VOICE) {
            ArrayList<String> values = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (values != null && !values.isEmpty()) draft.setText(LocalParser.appendVoice(draft.getText().toString(), values.get(0)));
        } else if (request == REQ_IMAGES) {
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
            } else if (data.getData() != null) uris.add(data.getData());
            importImages(uris);
        }
    }

    private void pickImages() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQ_IMAGES);
    }

    private void pasteImage() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = clipboard.getPrimaryClip();
        Uri uri = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).getUri() : null;
        if (uri == null) {
            toast("剪貼簿沒有可用圖片，請改用分享或相簿");
            return;
        }
        importImages(Collections.singletonList(uri));
    }

    private void handleShare(Intent intent) {
        if (intent == null) return;
        boolean isImage = intent.getType() != null && intent.getType().startsWith("image/");
        ShareModeResolver.Mode mode = ShareModeResolver.resolve(intent.getAction(), isImage);
        if (mode == ShareModeResolver.Mode.NONE) return;
        List<Uri> uris = new ArrayList<>();
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                Uri uri = intent.getClipData().getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        }
        if (uris.isEmpty() && Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> shared = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (shared != null) uris.addAll(shared);
        }
        if (uris.isEmpty()) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) uris.add(uri);
        }
        if (uris.isEmpty()) return;
        if (mode == ShareModeResolver.Mode.OCR) recognizeScreenshot(uris.get(0));
        else importImages(uris);
    }

    private void recognizeScreenshot(Uri uri) {
        final long targetListId = activeListId();
        final AiProvider targetProvider = selectedProvider();
        status.setText("正在讀取截圖並建立清單…");
        executor.execute(() -> {
            byte[] image = readScreenshotJpeg(uri);
            String key = keyStore.getForProvider(targetProvider.id());
            DeepSeekClient.Result result = image == null
                    ? new DeepSeekClient.Result(new ArrayList<>(), true, "無法讀取截圖，請重新截圖後分享")
                    : targetProvider == AiProvider.GEMINI
                            ? new GeminiClient().organizeImage(key, image)
                            : DeepSeekClient.organizeImage(key, image);
            String saveError = null;
            if (!result.items.isEmpty()) {
                try {
                    db.appendItems(targetListId, result.items);
                } catch (RuntimeException error) {
                    saveError = "加入清單失敗，請重新辨識";
                }
            }
            final String message = saveError == null ? result.message : saveError;
            runOnUiThread(() -> {
                if (listWorkflow.activeListId() == targetListId) {
                    status.setText(message);
                    render();
                }
            });
        });
    }

    private byte[] readScreenshotJpeg(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap source = BitmapFactory.decodeStream(input);
            if (source == null) return null;
            int width = source.getWidth();
            int height = source.getHeight();
            int max = Math.max(width, height);
            Bitmap output = source;
            if (max > 1600) {
                float scale = 1600f / max;
                output = Bitmap.createScaledBitmap(source, Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)), true);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            output.compress(Bitmap.CompressFormat.JPEG, 82, bytes);
            if (output != source) output.recycle();
            source.recycle();
            return bytes.toByteArray();
        } catch (Exception error) {
            return null;
        }
    }

    private void importImages(List<Uri> uris) {
        final long targetListId = activeListId();
        executor.execute(() -> {
            int added = 0;
            int failed = 0;
            File directory = new File(getFilesDir(), "reference-images");
            directory.mkdirs();
            for (Uri uri : uris) {
                File output = new File(directory, UUID.randomUUID() + ".img");
                try (InputStream input = getContentResolver().openInputStream(uri); FileOutputStream file = new FileOutputStream(output)) {
                    if (input == null) throw new IllegalStateException("empty image");
                    byte[] buffer = new byte[16384];
                    int read;
                    long total = 0;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 20L * 1024 * 1024) throw new IllegalStateException("image too large");
                        file.write(buffer, 0, read);
                    }
                    if (db.addImage(targetListId, output.getAbsolutePath()) < 0) throw new IllegalStateException("database insert failed");
                    added++;
                } catch (Exception error) {
                    output.delete();
                    failed++;
                }
            }
            final int successCount = added;
            final int failedCount = failed;
            runOnUiThread(() -> {
                if (activeListId() == targetListId) renderImages();
                toast("已加入 " + successCount + " 張" + (failedCount > 0 ? "，失敗 " + failedCount + " 張" : ""));
            });
        });
    }

    private Bitmap sample(String path, int target) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > target * 2 || bounds.outHeight / sample > target * 2) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        return BitmapFactory.decodeFile(path, options);
    }

    private void showImage(String path) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView view = new ImageView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(sample(path, Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels)));
        view.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    private void pasteText() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) {
            String text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this).toString();
            draft.setText(LocalParser.appendVoice(draft.getText().toString(), text));
        } else toast("剪貼簿沒有文字");
    }

    private void searchImage(String name) {
        try {
            Uri uri = Uri.parse("https://www.google.com/search?tbm=isch&q=" + URLEncoder.encode(name, "UTF-8"));
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception error) {
            toast("找不到可開啟網頁的瀏覽器");
        }
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        db.close();
        super.onDestroy();
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        return view;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView title(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(Color.rgb(30, 55, 32));
        view.setPadding(0, dp(10), 0, dp(7));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.DKGRAY);
        view.setPadding(dp(6), dp(7), dp(6), dp(7));
        return view;
    }

    private EditText input(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setSingleLine(true);
        return edit;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
