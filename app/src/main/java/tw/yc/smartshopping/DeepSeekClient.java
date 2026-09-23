package tw.yc.smartshopping;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class DeepSeekClient {
    public static final String MODEL = "deepseek-flash";
    private DeepSeekClient() {}

    public static Result organize(String key, String source) {
        if (key == null || key.trim().isEmpty()) return Result.fallback("尚未設定 API Key", source);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.deepseek.com/chat/completions").openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(30000); connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("Content-Type", "application/json"); connection.setDoOutput(true);
            JSONObject request = new JSONObject(); request.put("model", MODEL); request.put("temperature", 0);
            request.put("thinking", new JSONObject().put("type", "disabled"));
            request.put("response_format", new JSONObject().put("type", "json_object"));
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", "你是繁體中文購物清單整理器。只輸出JSON：{items:[{name,quantity,store,category}]}。保留所有商品，不可臆測數量。store只用好市多、全聯或未指定；依商店及類別排序。"));
            messages.put(new JSONObject().put("role", "user").put("content", source)); request.put("messages", messages);
            try (OutputStream out = connection.getOutputStream()) { out.write(request.toString().getBytes("UTF-8")); }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String reason = code == 401 || code == 403 ? "API Key 無效" : code == 429 ? "請求過多，已改用本機整理" : "AI 連線失敗";
                return Result.fallback(reason, source);
            }
            String body = read(connection.getInputStream());
            String content = new JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            JSONArray rows = new JSONObject(content).getJSONArray("items");
            List<LocalParser.ItemDraft> items = new ArrayList<>();
            for (int i=0; i<rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i); String name = row.optString("name").trim(); if (name.isEmpty()) continue;
                items.add(new LocalParser.ItemDraft(name, row.optString("quantity").trim(), blank(row.optString("store"), "未指定"), blank(row.optString("category"), "其他"), i));
            }
            if (items.isEmpty()) return Result.fallback("AI 格式無效，已改用本機整理", source);
            return new Result(items, false, "AI 整理完成");
        } catch (Exception error) { return Result.fallback("無法連線，已改用本機整理", source); }
        finally { if (connection != null) connection.disconnect(); }
    }

    public static Result organizeImage(String key, byte[] jpeg) {
        if (key == null || key.trim().isEmpty()) return new Result(new ArrayList<>(), true, "請先到設定輸入 DeepSeek API Key，再重新分享截圖");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.deepseek.com/chat/completions").openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(45000); connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + key.trim());
            connection.setRequestProperty("Content-Type", "application/json"); connection.setDoOutput(true);
            JSONObject request = new JSONObject(); request.put("model", MODEL); request.put("temperature", 0);
            request.put("thinking", new JSONObject().put("type", "disabled"));
            request.put("response_format", new JSONObject().put("type", "json_object"));
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", "讀取這張購物截圖中的商品文字。忽略聊天姓名、時間、價格合計與介面文字。只輸出JSON：{items:[{name,quantity,store,category}]}。不可臆測看不到的內容；store只用好市多、全聯或未指定。"));
            content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + Base64.encodeToString(jpeg, Base64.NO_WRAP))));
            JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
            request.put("messages", messages);
            try (OutputStream out = connection.getOutputStream()) { out.write(request.toString().getBytes("UTF-8")); }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String reason = code == 401 || code == 403 ? "API Key 無效，請到設定重新輸入" : code == 429 ? "目前請求過多，請稍後重新分享截圖" : "截圖辨識失敗，請檢查網路";
                return new Result(new ArrayList<>(), true, reason);
            }
            String body = read(connection.getInputStream());
            String responseText = new JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
            JSONArray rows = new JSONObject(responseText).getJSONArray("items");
            List<LocalParser.ItemDraft> items = new ArrayList<>();
            for (int i=0; i<rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i); String name = row.optString("name").trim(); if (name.isEmpty()) continue;
                items.add(new LocalParser.ItemDraft(name, row.optString("quantity").trim(), blank(row.optString("store"), "未指定"), blank(row.optString("category"), "其他"), i));
            }
            if (items.isEmpty()) return new Result(items, true, "截圖中沒有辨識到商品文字");
            return new Result(items, false, "截圖已轉成購物清單");
        } catch (Exception error) {
            return new Result(new ArrayList<>(), true, "截圖辨識失敗，請檢查網路後重試");
        } finally { if (connection != null) connection.disconnect(); }
    }
    public static String test(String key) { Result r = organize(key, "牛奶"); return r.fallback ? r.message : "連線成功"; }
    private static String blank(String text, String fallback) { return text == null || text.trim().isEmpty() ? fallback : text.trim(); }
    private static String read(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder(); String line;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) { while ((line=reader.readLine()) != null) result.append(line); }
        return result.toString();
    }
    public static final class Result {
        public final List<LocalParser.ItemDraft> items; public final boolean fallback; public final String message;
        Result(List<LocalParser.ItemDraft> items, boolean fallback, String message) { this.items=items; this.fallback=fallback; this.message=message; }
        static Result fallback(String message, String source) { return new Result(LocalParser.parse(source), true, message); }
    }
}
