package tw.yc.smartshopping;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsActivity extends Activity {
    private static final String PREF_PROVIDER = "ai_provider";
    private EditText keyInput;
    private SecureKeyStore keyStore;
    private SharedPreferences settings;
    private RadioGroup providerSelector;
    private RadioButton geminiOption;
    private RadioButton deepSeekOption;
    private TextView providerName;
    private TextView modelName;
    private AiProvider activeProvider;
    private String pendingGeminiInput;
    private String pendingDeepSeekInput;
    private boolean changingSelection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        keyStore = new SecureKeyStore(this);
        settings = getSharedPreferences("shopping_settings", MODE_PRIVATE);
        activeProvider = AiProvider.resolve(settings.getString(PREF_PROVIDER, null));
        settings.edit().putString(PREF_PROVIDER, activeProvider.id()).apply();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(32));
        scroll.addView(root);

        Button back = button("← 返回");
        root.addView(back);
        back.setOnClickListener(view -> finish());
        root.addView(title("AI 服務設定", 28));
        root.addView(label("選擇要使用的 AI 服務"));

        providerSelector = new RadioGroup(this);
        providerSelector.setId(R.id.aiProviderSelector);
        providerSelector.setOrientation(RadioGroup.VERTICAL);
        geminiOption = radio(getString(R.string.provider_gemini), R.id.geminiProviderOption);
        deepSeekOption = radio(getString(R.string.provider_deepseek), R.id.deepSeekProviderOption);
        providerSelector.addView(geminiOption);
        providerSelector.addView(deepSeekOption);
        root.addView(providerSelector, matchWrap());

        providerName = title("", 21);
        modelName = label("");
        root.addView(providerName);
        root.addView(modelName);

        keyInput = new EditText(this);
        keyInput.setId(R.id.apiKeyInput);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(keyInput, matchWrap());
        root.addView(label("每位使用者需自行輸入 API Key；金鑰會使用 Android Keystore 加密保存。"));
        root.addView(label("Gemini 免費方案與配額依 Google 當前政策及專案設定而異。"));

        LinearLayout actions = row();
        Button paste = button("貼上");
        Button save = button("儲存");
        Button clear = button("清除此服務金鑰");
        actions.addView(paste, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(clear, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);

        Button test = button("測試連線");
        root.addView(test, matchWrap());
        Switch imageSearch = new Switch(this);
        imageSearch.setId(R.id.imageSearchSwitch);
        imageSearch.setText("顯示搜尋圖片功能");
        imageSearch.setTextSize(17);
        imageSearch.setPadding(0, dp(20), 0, dp(8));
        imageSearch.setChecked(settings.getBoolean("image_search_enabled", false));
        root.addView(imageSearch, matchWrap());
        root.addView(label("預設關閉。開啟後才會在商品旁顯示搜尋圖片按鈕。"));

        changingSelection = true;
        (activeProvider == AiProvider.GEMINI ? geminiOption : deepSeekOption).setChecked(true);
        changingSelection = false;
        updateProviderFields();

        providerSelector.setOnCheckedChangeListener((group, checkedId) -> {
            if (changingSelection) return;
            AiProvider next = checkedId == R.id.deepSeekProviderOption ? AiProvider.DEEPSEEK : AiProvider.GEMINI;
            if (next == activeProvider) return;
            rememberCurrentInput();
            activeProvider = next;
            settings.edit().putString(PREF_PROVIDER, activeProvider.id()).apply();
            updateProviderFields();
        });
        paste.setOnClickListener(view -> paste());
        save.setOnClickListener(view -> saveKey());
        clear.setOnClickListener(view -> clearCurrentKey());
        test.setOnClickListener(view -> testConnection(test));
        imageSearch.setOnCheckedChangeListener((button, checked) -> settings.edit().putBoolean("image_search_enabled", checked).apply());
        setContentView(scroll);
    }

    private RadioButton radio(String text, int id) {
        RadioButton radio = new RadioButton(this);
        radio.setText(text);
        radio.setId(id);
        radio.setTextSize(17);
        return radio;
    }

    private void updateProviderFields() {
        if (activeProvider == AiProvider.GEMINI) {
            providerName.setText("Gemini");
            modelName.setText("模型：" + AiProvider.DEFAULT_MODEL);
            keyInput.setHint("貼上 Google AI Studio Gemini API Key");
            keyInput.setText(pendingGeminiInput != null ? pendingGeminiInput : value(keyStore.getForProvider(activeProvider.id())));
        } else {
            providerName.setText("DeepSeek");
            modelName.setText("模型：" + DeepSeekClient.MODEL);
            keyInput.setHint("貼上 DeepSeek API Key");
            keyInput.setText(pendingDeepSeekInput != null ? pendingDeepSeekInput : value(keyStore.getForProvider(activeProvider.id())));
        }
        keyInput.setSelection(keyInput.getText().length());
    }

    private void rememberCurrentInput() {
        if (activeProvider == AiProvider.GEMINI) pendingGeminiInput = keyInput.getText().toString();
        else pendingDeepSeekInput = keyInput.getText().toString();
    }

    private void setPendingInput(String value) {
        if (activeProvider == AiProvider.GEMINI) pendingGeminiInput = value;
        else pendingDeepSeekInput = value;
    }

    private void paste() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()) keyInput.setText(clipboard.getPrimaryClip().getItemAt(0).coerceToText(this));
        else toast("剪貼簿沒有文字");
    }

    private boolean saveKey() {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            toast("請輸入 API Key");
            return false;
        }
        try {
            keyStore.saveForProvider(activeProvider.id(), key);
            setPendingInput(null);
            toast("API Key 已加密儲存");
            return true;
        } catch (Exception error) {
            toast("無法儲存，請重新嘗試");
            return false;
        }
    }

    private void clearCurrentKey() {
        keyStore.clearForProvider(activeProvider.id());
        setPendingInput("");
        keyInput.setText("");
        toast(activeProvider == AiProvider.GEMINI ? "Gemini API Key 已清除" : "DeepSeek API Key 已清除");
    }

    private void testConnection(Button test) {
        if (!saveKey()) return;
        AiProvider target = activeProvider;
        test.setEnabled(false);
        executor.execute(() -> {
            String key = keyStore.getForProvider(target.id());
            String message = target == AiProvider.GEMINI
                    ? new GeminiClient().test(key)
                    : DeepSeekClient.test(key);
            runOnUiThread(() -> {
                test.setEnabled(true);
                toast(message);
            });
        });
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
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
        view.setTextColor(Color.rgb(30, 50, 30));
        view.setPadding(0, dp(12), 0, dp(8));
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(15);
        view.setTextColor(Color.DKGRAY);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_SHORT).show(); }
    private String value(String text) { return text == null ? "" : text; }
}
