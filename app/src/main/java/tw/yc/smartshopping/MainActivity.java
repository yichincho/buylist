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
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextUtils;
import android.graphics.Typeface;
import android.text.style.StyleSpan;
import android.text.style.ForegroundColorSpan;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
    private final List<DragListLayout> groupLists = new ArrayList<>();
    private TextView listSummary;
    private ProgressBar listProgress;
    private TextView hint;
    private Ui ui;
    private LinearLayout storeChoices;
    private String chosenStore = ShoppingListModel.DEFAULT_STORE;
    private GridLayout imageGrid;
    private TextView status;
    private LinearLayout undoBar;
    private TextView undoText;
    private Runnable undoAction;
    private final Runnable hideUndo = () -> showUndo(null, null);
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
        ui = new Ui(this);
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
        FrameLayout frame = new FrameLayout(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout root = column();
        root.setPadding(dp(16), dp(12), dp(16), dp(96));
        scroll.addView(root);
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        // App bar: title, current list and the two navigation actions.
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = column();
        heading.addView(ui.text(getString(R.string.app_name), 26, R.color.text_primary, true));
        currentListLabel = ui.text("", 14, R.color.text_secondary, false);
        currentListLabel.setId(R.id.currentListTitle);
        currentListLabel.setSingleLine(true);
        currentListLabel.setEllipsize(TextUtils.TruncateAt.END);
        heading.addView(currentListLabel);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button history = ui.tonalButton(getString(R.string.list_history));
        history.setId(R.id.listHistoryButton);
        Button settings = ui.tonalButton("設定");
        header.addView(history, ui.margins(-2, dp(44), 6, 0, 0, 0));
        header.addView(settings, ui.margins(-2, dp(44), 6, 0, 0, 0));
        root.addView(header, ui.margins(-1, -2, 4, 6, 0, 18));
        history.setOnClickListener(v -> showHistory());
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // Input card.
        LinearLayout input = ui.card();
        input.setPadding(dp(14), dp(14), dp(14), dp(12));
        draft = new EditText(this);
        draft.setId(R.id.inputDraft);
        draft.setHint("貼上或輸入要買的東西…\n例如：好市多牛奶、全聯雞蛋、衛生紙 2 包");
        draft.setMinLines(3);
        draft.setGravity(Gravity.TOP);
        ui.styleField(draft);
        draft.setText(preferences.getString(draftKey(activeListId()), ""));
        input.addView(draft, matchWrap());
        draft.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressDraftSave) saveDraft(activeListId(), s.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });

        LinearLayout inputTools = row();
        Button paste = ui.textButton("貼上", R.color.accent);
        Button voice = ui.textButton("🎤 語音", R.color.accent);
        Button clearText = ui.textButton(getString(R.string.clear_text), R.color.text_secondary);
        clearText.setId(R.id.clearDraftButton);
        inputTools.addView(paste, new LinearLayout.LayoutParams(-2, dp(44)));
        inputTools.addView(voice, new LinearLayout.LayoutParams(-2, dp(44)));
        inputTools.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        inputTools.addView(clearText, new LinearLayout.LayoutParams(-2, dp(44)));
        input.addView(inputTools, ui.margins(-1, -2, 0, 4, 0, 4));
        paste.setOnClickListener(v -> pasteText());
        voice.setOnClickListener(v -> requestVoice());
        clearText.setOnClickListener(v -> clearDraftText());

        input.addView(ui.text("放到哪個賣場", 13, R.color.text_secondary, true), ui.margins(-2, -2, 2, 0, 0, 8));
        HorizontalScrollView storeScroll = new HorizontalScrollView(this);
        storeScroll.setHorizontalScrollBarEnabled(false);
        storeScroll.setClipToPadding(false);
        storeChoices = row();
        storeScroll.addView(storeChoices);
        input.addView(storeScroll, matchWrap());

        LinearLayout draftActions = row();
        Button addDraft = ui.primaryButton(getString(R.string.add_text_to_list));
        addDraft.setId(R.id.addDraftButton);
        Button organize = ui.tonalButton("✨ " + getString(R.string.ai_organize_add));
        organize.setId(R.id.organizeButton);
        draftActions.addView(addDraft, new LinearLayout.LayoutParams(0, dp(48), 1));
        draftActions.addView(organize, weighted(8));
        input.addView(draftActions, ui.margins(-1, -2, 0, 14, 0, 0));
        addDraft.setOnClickListener(v -> addDraftToList());
        organize.setOnClickListener(v -> organize(organize));
        status = ui.text("", 14, R.color.text_secondary, false);
        status.setPadding(dp(2), dp(8), dp(2), 0);
        input.addView(status, matchWrap());
        root.addView(input, matchWrap());

        // Shopping list: summary, progress and one card per store.
        LinearLayout listHeader = row();
        listHeader.setGravity(Gravity.BOTTOM);
        listHeader.addView(ui.text("購物項目", 20, R.color.text_primary, true), new LinearLayout.LayoutParams(0, -2, 1));
        listSummary = ui.text("", 14, R.color.text_secondary, false);
        listHeader.addView(listSummary);
        root.addView(listHeader, ui.margins(-1, -2, 4, 24, 4, 8));
        listProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        listProgress.setProgressTintList(ColorStateList.valueOf(ui.color(R.color.accent)));
        listProgress.setProgressBackgroundTintList(ColorStateList.valueOf(ui.color(R.color.border_subtle)));
        root.addView(listProgress, ui.margins(-1, dp(6), 4, 0, 4, 12));

        listContainer = column();
        listContainer.setId(R.id.shoppingListContainer);
        root.addView(listContainer, matchWrap());

        LinearLayout itemActions = row();
        Button addItem = ui.tonalButton("＋ 新增品項");
        Button clearCompleted = ui.outlineButton("清除已買", R.color.text_primary);
        itemActions.addView(addItem, new LinearLayout.LayoutParams(0, dp(48), 1));
        itemActions.addView(clearCompleted, weighted(8));
        root.addView(itemActions, ui.margins(-1, -2, 0, 2, 0, 0));
        addItem.setOnClickListener(v -> addItemDialog());
        clearCompleted.setOnClickListener(v -> confirmClearCompleted());
        hint = ui.text("點品項可編輯　長按或按住 ⠿ 拖曳排序　左右滑動刪除", 13, R.color.text_secondary, false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, ui.margins(-1, -2, 0, 10, 0, 0));

        // Reference images.
        LinearLayout images = ui.card();
        images.setPadding(dp(14), dp(14), dp(14), dp(14));
        images.addView(ui.text("商品圖片", 18, R.color.text_primary, true));
        images.addView(ui.text("不必對應品項。可從 LINE 分享、相簿選擇或貼上剪貼簿圖片。", 13, R.color.text_secondary, false),
                ui.margins(-1, -2, 0, 2, 0, 10));
        imageGrid = new GridLayout(this);
        imageGrid.setId(R.id.imageGrid);
        imageGrid.setColumnCount(2);
        images.addView(imageGrid, matchWrap());
        LinearLayout imageActions = row();
        Button gallery = ui.tonalButton("＋ 相簿圖片");
        Button clipImage = ui.outlineButton("貼上圖片", R.color.text_primary);
        imageActions.addView(gallery, new LinearLayout.LayoutParams(0, dp(48), 1));
        imageActions.addView(clipImage, weighted(8));
        images.addView(imageActions, ui.margins(-1, -2, 0, 10, 0, 0));
        root.addView(images, ui.margins(-1, -2, 0, 24, 0, 0));
        gallery.setOnClickListener(v -> pickImages());
        clipImage.setOnClickListener(v -> pasteImage());

        // Whole-list actions.
        LinearLayout listActions = ui.card();
        listActions.setPadding(dp(14), dp(14), dp(14), dp(10));
        listActions.addView(ui.text("清單", 18, R.color.text_primary, true), ui.margins(-2, -2, 0, 0, 0, 8));
        Button saveAndNew = ui.tonalButton(getString(R.string.save_and_new_list));
        saveAndNew.setId(R.id.saveAndNewListButton);
        Button clearList = ui.textButton(getString(R.string.clear_whole_list), R.color.negative);
        clearList.setId(R.id.clearWholeListButton);
        listActions.addView(saveAndNew, new LinearLayout.LayoutParams(-1, dp(48)));
        listActions.addView(clearList, ui.margins(-1, dp(48), 0, 4, 0, 0));
        root.addView(listActions, ui.margins(-1, -2, 0, 16, 0, 0));
        saveAndNew.setOnClickListener(v -> saveAndStartNewList());
        clearList.setOnClickListener(v -> confirmClearCurrentList());

        // Floating undo snackbar.
        undoBar = row();
        undoBar.setGravity(Gravity.CENTER_VERTICAL);
        undoBar.setPadding(dp(18), dp(4), dp(6), dp(4));
        undoBar.setBackground(ui.shape(ui.color(R.color.inverse_bg), Ui.RADIUS_ELEMENT, 0));
        undoBar.setElevation(dp(6));
        undoText = ui.text("", 15, R.color.inverse_text, false);
        undoText.setSingleLine(true);
        undoText.setEllipsize(TextUtils.TruncateAt.END);
        undoBar.addView(undoText, new LinearLayout.LayoutParams(0, -2, 1));
        Button undo = ui.textButton("復原", R.color.inverse_accent);
        undoBar.addView(undo, new LinearLayout.LayoutParams(-2, dp(44)));
        undo.setOnClickListener(v -> {
            Runnable action = undoAction;
            showUndo(null, null);
            if (action != null) action.run();
        });
        undoBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams undoParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        undoParams.setMargins(dp(16), 0, dp(16), dp(20));
        frame.addView(undoBar, undoParams);

        updateCurrentListLabel();
        setContentView(frame);
    }

    private void addDraftToList() {
        String text = draft.getText().toString();
        if (text.trim().isEmpty()) {
            toast("請先輸入購物內容");
            return;
        }
        try {
            int count = listWorkflow.addText(text, knownStores(), chosenStore);
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
        final String targetStore = chosenStore;
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
                    db.appendItems(targetListId, LocalParser.withStore(result.items, targetStore));
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
        renderStoreChoices();
        renderItems();
        renderImages();
    }

    private void renderItems() {
        listContainer.removeAllViews();
        groupLists.clear();
        final long listId = activeListId();
        List<ShoppingDb.Item> stored = db.items(listId);
        List<String> storedKeys = new ArrayList<>();
        List<Boolean> storedDone = new ArrayList<>();
        for (ShoppingDb.Item item : stored) {
            storedKeys.add(groupKey(item));
            storedDone.add(item.done);
        }
        final List<ShoppingDb.Item> items = new ArrayList<>();
        for (int index : ShoppingListModel.groupedOrder(storedKeys, storedDone)) items.add(stored.get(index));

        int doneCount = 0;
        for (ShoppingDb.Item item : items) if (item.done) doneCount++;
        listSummary.setText(items.isEmpty() ? "" : "已買 " + doneCount + " / " + items.size());
        listProgress.setMax(Math.max(1, items.size()));
        listProgress.setProgress(doneCount);
        listProgress.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        hint.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);

        if (items.isEmpty()) {
            LinearLayout empty = ui.card();
            empty.setPadding(dp(18), dp(28), dp(18), dp(28));
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.addView(ui.text("🛒", 34, R.color.text_primary, false));
            empty.addView(ui.text("清單是空的", 17, R.color.text_primary, true), ui.margins(-2, -2, 0, 6, 0, 2));
            TextView tip = ui.text("在上方輸入要買的東西，按「加入清單」。", 14, R.color.text_secondary, false);
            tip.setGravity(Gravity.CENTER);
            empty.addView(tip);
            listContainer.addView(empty, ui.margins(-1, -2, 0, 0, 0, 12));
            return;
        }

        boolean search = getSharedPreferences("shopping_settings", MODE_PRIVATE).getBoolean("image_search_enabled", false);
        DragListLayout.OnSwipeListener onSwipe = id -> {
            for (ShoppingDb.Item item : items) if (item.id == id) deleteWithUndo(item, listId);
        };
        DragListLayout.OnReorderListener onReorder = ids -> {
            List<Long> all = new ArrayList<>();
            for (DragListLayout group : groupLists) all.addAll(group.rowIds());
            db.reorderItems(listId, all);
            if (activeListId() == listId) renderItems();
        };

        int start = 0;
        while (start < items.size()) {
            String key = groupKey(items.get(start));
            int end = start;
            int groupDone = 0;
            while (end < items.size() && groupKey(items.get(end)).equals(key)) {
                if (items.get(end).done) groupDone++;
                end++;
            }
            LinearLayout card = ui.card();
            card.addView(groupHeader(items.get(start), listId, groupDone, end - start));
            DragListLayout rows = new DragListLayout(this);
            rows.setOnSwipeListener(onSwipe);
            rows.setOnReorderListener(onReorder);
            for (int position = start; position < end; position++) {
                rows.addView(itemRow(items.get(position), listId, rows, (position - start) % 2 == 1, search), matchWrap());
            }
            groupLists.add(rows);
            card.addView(rows, matchWrap());
            listContainer.addView(card, ui.margins(-1, -2, 0, 0, 0, 12));
            start = end;
        }
    }

    private View itemRow(ShoppingDb.Item item, long listId, DragListLayout rows, boolean alternate, boolean search) {
        FrameLayout row = new FrameLayout(this);

        // Revealed while swiping: a red strip with a delete label on each side.
        FrameLayout backdrop = new FrameLayout(this);
        backdrop.setBackgroundColor(ui.color(R.color.negative));
        backdrop.setVisibility(View.INVISIBLE);
        for (int gravity : new int[]{Gravity.START, Gravity.END}) {
            TextView label = ui.text("🗑 刪除", 15, R.color.on_negative, true);
            label.setPadding(dp(22), 0, dp(22), 0);
            backdrop.addView(label, new FrameLayout.LayoutParams(-2, -2, gravity | Gravity.CENTER_VERTICAL));
        }
        row.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = row();
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setMinimumHeight(dp(60));
        content.setBackgroundColor(ui.color(alternate ? R.color.card_alt : R.color.card));
        row.addView(content, new FrameLayout.LayoutParams(-1, -2));
        // Checked items stay below unchecked ones, so each can only be dragged among its own kind.
        row.setTag(new DragListLayout.Row(item.id, groupKey(item) + (item.done ? "｜done" : ""), content, backdrop));

        FrameLayout checkArea = new FrameLayout(this);
        CheckBox check = new CheckBox(this);
        check.setChecked(item.done);
        check.setScaleX(1.45f);
        check.setScaleY(1.45f);
        ui.tint(check);
        check.setContentDescription(item.name + " 已買");
        checkArea.addView(check, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        checkArea.setOnClickListener(v -> check.toggle());
        content.addView(checkArea, new LinearLayout.LayoutParams(dp(60), dp(60)));

        LinearLayout texts = row();
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setMinimumHeight(dp(60));
        texts.setPadding(0, dp(8), dp(4), dp(8));
        // Quantity sits right after the name, highlighted, e.g. "牛奶  2 瓶".
        SpannableStringBuilder label = new SpannableStringBuilder(item.name);
        if (!item.quantity.isEmpty()) {
            int start = label.length() + 2;
            label.append("  ").append(item.quantity);
            if (!item.done) label.setSpan(new ForegroundColorSpan(ui.color(R.color.accent)), start, label.length(), 0);
            label.setSpan(new StyleSpan(Typeface.BOLD), start, label.length(), 0);
        }
        TextView name = ui.text("", 17, item.done ? R.color.text_disabled : R.color.text_primary, !item.done);
        name.setText(label);
        if (item.done) name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        texts.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (search) {
            Button searchButton = ui.textButton("找圖", R.color.accent);
            content.addView(searchButton, new LinearLayout.LayoutParams(-2, dp(44)));
            searchButton.setOnClickListener(v -> searchImage(item.name));
        }
        TextView handle = ui.text("⠿", 22, R.color.text_disabled, false);
        handle.setGravity(Gravity.CENTER);
        handle.setContentDescription("拖曳移動 " + item.name);
        content.addView(handle, new LinearLayout.LayoutParams(dp(48), dp(60)));
        handle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) rows.startDrag(row, event.getRawY());
            return true;
        });

        final float[] touchY = new float[1];
        View.OnTouchListener rememberTouch = (v, event) -> {
            touchY[0] = event.getRawY();
            return false;
        };
        View.OnLongClickListener drag = v -> {
            rows.startDrag(row, touchY[0]);
            return true;
        };
        texts.setOnTouchListener(rememberTouch);
        texts.setOnLongClickListener(drag);
        texts.setBackground(ui.rowRipple());
        texts.setOnClickListener(v -> editItemDialog(item, listId));

        check.setOnCheckedChangeListener((button, checked) -> {
            db.toggleItem(listId, item.id, checked);
            if (activeListId() == listId) renderItems();
        });
        return row;
    }

    private String groupKey(ShoppingDb.Item item) {
        return ShoppingListModel.normalizeStore(item.store) + "｜" + item.category;
    }

    private View groupHeader(ShoppingDb.Item first, long listId, int done, int total) {
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(52));
        header.setPadding(dp(16), dp(6), dp(8), dp(6));
        header.setBackgroundColor(ui.color(R.color.accent_header));
        String store = ShoppingListModel.normalizeStore(first.store);
        header.addView(ui.text(store, 17, R.color.text_primary, true));
        if (!"其他".equals(first.category)) {
            header.addView(ui.text("・" + first.category, 15, R.color.text_secondary, false));
        }
        header.addView(ui.badge(done + "/" + total), ui.margins(-2, -2, 8, 0, 0, 0));
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        if (ShoppingListModel.UNASSIGNED_STORE.equals(store)) {
            Button assign = ui.textButton("指定賣場", R.color.accent);
            header.addView(assign, new LinearLayout.LayoutParams(-2, dp(40)));
            assign.setOnClickListener(v -> assignUnassignedItems(listId));
        }
        return header;
    }

    private interface StoreCallback { void onStore(String store); }

    private List<String> customStores() {
        return ShoppingListModel.decodeStores(getSharedPreferences("shopping_settings", MODE_PRIVATE).getString("custom_stores", ""));
    }

    private void saveCustomStore(String store) {
        List<String> stores = customStores();
        if (!stores.contains(store)) stores.add(store);
        getSharedPreferences("shopping_settings", MODE_PRIVATE).edit()
                .putString("custom_stores", ShoppingListModel.encodeStores(stores)).apply();
    }

    private List<String> knownStores() {
        return ShoppingListModel.storeOptions(customStores(), db.usedStores());
    }

    private void renderStoreChoices() {
        storeChoices.removeAllViews();
        List<String> stores = new ArrayList<>(knownStores());
        stores.remove(ShoppingListModel.UNASSIGNED_STORE);
        if (!stores.contains(chosenStore)) chosenStore = ShoppingListModel.DEFAULT_STORE;
        for (String store : stores) storeChoices.addView(storeChoice(store, store), ui.margins(-2, -2, 0, 0, 8, 0));
        TextView add = ui.chip("＋ 新增", false);
        add.setTextColor(ui.color(R.color.accent));
        storeChoices.addView(add);
        add.setOnClickListener(v -> addStoreDialog(store -> {
            chosenStore = store;
            renderStoreChoices();
        }));
    }

    private TextView storeChoice(String label, String store) {
        boolean selected = store.equals(chosenStore);
        TextView choice = ui.chip(selected ? "✓ " + label : label, selected);
        choice.setContentDescription(label + (selected ? "，已選取" : ""));
        choice.setOnClickListener(v -> {
            chosenStore = store;
            renderStoreChoices();
        });
        return choice;
    }

    private void chooseStore(String current, StoreCallback callback) {
        final List<String> options = knownStores();
        String[] labels = new String[options.size() + 1];
        for (int i = 0; i < options.size(); i++) {
            labels[i] = options.get(i) + (options.get(i).equals(ShoppingListModel.normalizeStore(current)) ? "（目前）" : "");
        }
        labels[options.size()] = "＋ 新增賣場…";
        new AlertDialog.Builder(this).setTitle("選擇賣場")
                .setItems(labels, (dialog, which) -> {
                    if (which < options.size()) callback.onStore(options.get(which));
                    else addStoreDialog(callback);
                })
                .setNegativeButton("取消", null).show();
    }

    private void addStoreDialog(StoreCallback callback) {
        EditText name = input("賣場名稱，例如：家樂福、傳統市場", "");
        LinearLayout form = column();
        form.setPadding(dp(18), 0, dp(18), 0);
        form.addView(name, matchWrap());
        new AlertDialog.Builder(this).setTitle("新增賣場").setView(form)
                .setPositiveButton("新增", (dialog, which) -> {
                    String store = name.getText().toString().trim();
                    if (store.isEmpty()) {
                        toast("請輸入賣場名稱");
                        return;
                    }
                    saveCustomStore(store);
                    callback.onStore(store);
                }).setNegativeButton("取消", null).show();
    }

    private void assignUnassignedItems(long listId) {
        final List<ShoppingDb.Item> unassigned = new ArrayList<>();
        for (ShoppingDb.Item item : db.items(listId)) {
            if (ShoppingListModel.UNASSIGNED_STORE.equals(ShoppingListModel.normalizeStore(item.store))) unassigned.add(item);
        }
        if (unassigned.isEmpty()) return;
        chooseStore(ShoppingListModel.UNASSIGNED_STORE, store -> {
            if (ShoppingListModel.UNASSIGNED_STORE.equals(store)) return;
            String[] labels = new String[unassigned.size()];
            final boolean[] selected = new boolean[unassigned.size()];
            for (int i = 0; i < unassigned.size(); i++) labels[i] = unassigned.get(i).name;
            new AlertDialog.Builder(this).setTitle("哪些品項要移到「" + store + "」？")
                    .setMultiChoiceItems(labels, selected, (dialog, which, checked) -> selected[which] = checked)
                    .setPositiveButton("移動", (dialog, which) -> {
                        List<Long> ids = new ArrayList<>();
                        for (int i = 0; i < unassigned.size(); i++) if (selected[i]) ids.add(unassigned.get(i).id);
                        if (ids.isEmpty()) {
                            toast("已新增賣場選項，未移動品項");
                            return;
                        }
                        db.moveItemsToStore(listId, ids, store);
                        status.setText("已將 " + ids.size() + " 項移到「" + store + "」");
                        if (activeListId() == listId) renderItems();
                    })
                    .setNegativeButton("取消", null).show();
        });
    }

    private void renderImages() {
        imageGrid.removeAllViews();
        final long listId = activeListId();
        List<ShoppingDb.ImageRow> images = db.images(listId);
        // Screen padding 16 + card padding 14 on each side, and 4dp around each tile.
        int width = (getResources().getDisplayMetrics().widthPixels - dp(2 * 16 + 2 * 14 + 4 * 4)) / 2;
        int number = 1;
        for (ShoppingDb.ImageRow image : images) {
            FrameLayout tile = new FrameLayout(this);
            tile.setBackground(ui.shape(ui.color(R.color.field), Ui.RADIUS_ELEMENT, 0));
            tile.setClipToOutline(true);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = width;
            params.height = width;
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            ImageView view = new ImageView(this);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setImageBitmap(sample(image.path, width));
            view.setAlpha(image.done ? .35f : 1f);
            tile.addView(view, new FrameLayout.LayoutParams(-1, -1));
            CheckBox done = new CheckBox(this);
            done.setChecked(image.done);
            done.setContentDescription("商品圖片 " + number + (image.done ? " 已完成" : " 完成狀態"));
            done.setScaleX(1.35f);
            done.setScaleY(1.35f);
            ui.tint(done);
            FrameLayout checkArea = new FrameLayout(this);
            checkArea.setBackground(ui.shape(ui.color(R.color.card), Ui.RADIUS_FULL, 0));
            checkArea.setElevation(dp(2));
            checkArea.addView(done, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            checkArea.setOnClickListener(v -> done.toggle());
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END);
            checkParams.setMargins(0, dp(6), dp(6), 0);
            tile.addView(checkArea, checkParams);
            done.setOnCheckedChangeListener((button, checked) -> {
                db.toggleImage(listId, image.id, checked);
                if (activeListId() == listId) renderImages();
            });
            view.setOnClickListener(v -> showImage(image.path));
            view.setOnLongClickListener(v -> {
                confirmDeleteImage(image, listId);
                return true;
            });
            imageGrid.addView(tile, params);
            number++;
        }
        if (images.isEmpty()) {
            TextView empty = ui.text("尚未加入圖片", 14, R.color.text_disabled, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(18), 0, dp(18));
            empty.setBackground(ui.shape(ui.color(R.color.field), Ui.RADIUS_ELEMENT, 0));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(0, 2, 1f));
            params.width = 0;
            imageGrid.addView(empty, params);
        }
    }

    private void editItemDialog(ShoppingDb.Item item, long listId) {
        LinearLayout form = column();
        form.setPadding(dp(18), 0, dp(18), 0);
        EditText name = input("商品名稱", item.name);
        EditText quantity = input("數量", item.quantity);
        final String[] store = {ShoppingListModel.normalizeStore(item.store)};
        Button storeButton = ui.outlineButton("賣場：" + store[0] + "（點此更改）", R.color.text_primary);
        storeButton.setOnClickListener(v -> chooseStore(store[0], picked -> {
            store[0] = picked;
            storeButton.setText("賣場：" + picked + "（點此更改）");
        }));
        form.addView(name);
        form.addView(quantity);
        form.addView(storeButton, matchWrap());
        new AlertDialog.Builder(this).setTitle("編輯品項").setView(form)
                .setPositiveButton("儲存", (dialog, which) -> {
                    if (!name.getText().toString().trim().isEmpty()) {
                        db.updateItem(listId, item.id, name.getText().toString().trim(), quantity.getText().toString().trim(), store[0]);
                        if (activeListId() == listId) renderItems();
                    }
                })
                .setNeutralButton("刪除", (dialog, which) -> confirmDeleteItem(item, listId))
                .setNegativeButton("取消", null).show();
    }

    private void addItemDialog() {
        EditText name = input("商品名稱", "");
        new AlertDialog.Builder(this).setTitle("新增品項").setView(name)
                .setPositiveButton("新增", (dialog, which) -> {
                    String value = name.getText().toString().trim();
                    if (!value.isEmpty()) {
                        db.appendItems(activeListId(), LocalParser.withStore(
                                Collections.singletonList(new LocalParser.ItemDraft(value, "", ShoppingListModel.UNASSIGNED_STORE, "其他", 0)), chosenStore));
                        renderItems();
                    }
                }).setNegativeButton("取消", null).show();
    }

    private void deleteWithUndo(ShoppingDb.Item item, long listId) {
        db.deleteItem(listId, item.id);
        if (activeListId() == listId) renderItems();
        showUndo("已刪除「" + item.name + "」", () -> {
            db.restoreItem(listId, item);
            if (activeListId() == listId) renderItems();
        });
    }

    private void showUndo(String message, Runnable action) {
        undoBar.removeCallbacks(hideUndo);
        undoAction = action;
        if (message == null) {
            undoBar.animate().alpha(0f).translationY(dp(24)).setDuration(150)
                    .withEndAction(() -> undoBar.setVisibility(View.GONE)).start();
            return;
        }
        undoText.setText(message);
        if (undoBar.getVisibility() != View.VISIBLE) {
            undoBar.setAlpha(0f);
            undoBar.setTranslationY(dp(24));
            undoBar.setVisibility(View.VISIBLE);
        }
        undoBar.animate().alpha(1f).translationY(0).setDuration(180).start();
        undoBar.postDelayed(hideUndo, 8000);
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

    private TextView label(String text) {
        TextView view = ui.text(text, 15, R.color.text_secondary, false);
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

    private LinearLayout.LayoutParams weighted(int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.leftMargin = dp(leftMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
}
