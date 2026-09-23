package tw.yc.smartshopping;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class GeminiClient {
    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private final String baseUrl;

    public GeminiClient() { this(DEFAULT_BASE_URL); }

    public GeminiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) throw new IllegalArgumentException("baseUrl is required");
        String value = baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        this.baseUrl = value;
    }

    public DeepSeekClient.Result organize(String key, String text) {
        if (blank(key)) return fallback(text, "尚未設定 Gemini API Key，已改用本機整理");
        if (blank(text)) return fallback(text, "請先輸入購物內容");
        return send(key, text, null, text);
    }

    public DeepSeekClient.Result organizeImage(String key, byte[] jpeg) {
        if (blank(key)) return imageFallback("尚未設定 Gemini API Key");
        if (jpeg == null || jpeg.length == 0) return imageFallback("沒有可辨識的截圖");
        return send(key, null, jpeg, "");
    }

    public String test(String key) {
        DeepSeekClient.Result result = organize(key, "牛奶");
        return result.fallback ? result.message : "連線成功";
    }

    private DeepSeekClient.Result send(String key, String text, byte[] jpeg, String fallbackText) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + "/v1beta/models/" + AiProvider.DEFAULT_MODEL + ":generateContent");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(jpeg == null ? 30000 : 45000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("x-goog-api-key", key.trim());
            connection.setDoOutput(true);
            byte[] request = requestBody(text, jpeg).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(request.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(request); }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) return errorResult(status, fallbackText, jpeg != null);
            String response = readUtf8(connection.getInputStream());
            return GeminiResponseParser.parse(response, fallbackText);
        } catch (SocketTimeoutException timeout) {
            return errorResult(0, fallbackText, jpeg != null);
        } catch (Exception error) {
            return jpeg == null
                    ? fallback(fallbackText, "Gemini 連線失敗，已改用本機整理")
                    : imageFallback("截圖辨識失敗，請檢查網路後重試");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String requestBody(String text, byte[] jpeg) {
        String instruction = jpeg == null
                ? "整理使用者提供的採買需求，保留所有品項與數量；不得刪除重複品項或自行猜測。只輸出 JSON，格式為 {\"items\":[{\"name\":\"\",\"quantity\":\"\",\"store\":\"\",\"category\":\"\"}]}。store 使用好市多、全聯或未指定；缺少 store 時填未指定，缺少 category 時填其他。"
                : "辨識這張採買截圖中的商品文字，忽略聊天姓名、時間、價格合計與介面文字。保留所有可辨識品項及數量，不可臆測。只輸出採買品項 JSON。";
        StringBuilder body = new StringBuilder();
        body.append("{\"systemInstruction\":{\"parts\":[{\"text\":").append(jsonString(instruction)).append("}]},");
        body.append("\"contents\":[{\"role\":\"user\",\"parts\":[");
        if (jpeg == null) {
            body.append("{\"text\":").append(jsonString("整理我貼上的需求：" + text)).append("}");
        } else {
            body.append("{\"text\":").append(jsonString("請辨識此購物清單截圖中的品項。只回傳 items JSON。"))
                    .append("},{\"inlineData\":{\"mimeType\":\"image/jpeg\",\"data\":")
                    .append(jsonString(encodeBase64(jpeg))).append("}}");
        }
        body.append("]}],\"generationConfig\":{\"responseMimeType\":\"application/json\",\"responseSchema\":{")
                .append("\"type\":\"OBJECT\",\"properties\":{\"items\":{\"type\":\"ARRAY\",\"items\":{")
                .append("\"type\":\"OBJECT\",\"properties\":{")
                .append("\"name\":{\"type\":\"STRING\"},\"quantity\":{\"type\":\"STRING\"},")
                .append("\"store\":{\"type\":\"STRING\"},\"category\":{\"type\":\"STRING\"}},")
                .append("\"required\":[\"name\",\"quantity\",\"store\",\"category\"]}}},\"required\":[\"items\"]}}}");
        return body.toString();
    }

    private DeepSeekClient.Result errorResult(int status, String originalText, boolean image) {
        String message;
        if (status == 401 || status == 403) message = "Gemini API Key 無效，請檢查設定";
        else if (status == 429) message = "Gemini API 配額已用完或請求過多";
        else if (status == 0) message = "Gemini 連線逾時";
        else message = "Gemini API 請求失敗（HTTP " + status + "）";
        if (image) return imageFallback(message);
        return fallback(originalText, message + "，已改用本機整理");
    }

    private DeepSeekClient.Result fallback(String text, String message) {
        return new DeepSeekClient.Result(LocalParser.parse(text), true, message);
    }

    private DeepSeekClient.Result imageFallback(String message) {
        return new DeepSeekClient.Result(new ArrayList<LocalParser.ItemDraft>(), true, message);
    }

    private String readUtf8(InputStream input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        try (InputStream stream = input) {
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        result.append("\\u");
                        for (int pad = hex.length(); pad < 4; pad++) result.append('0');
                        result.append(hex);
                    } else result.append(c);
            }
        }
        return result.append('"').toString();
    }

    private static String encodeBase64(byte[] input) {
        StringBuilder result = new StringBuilder(((input.length + 2) / 3) * 4);
        for (int i = 0; i < input.length; i += 3) {
            int first = input[i] & 0xff;
            int second = i + 1 < input.length ? input[i + 1] & 0xff : 0;
            int third = i + 2 < input.length ? input[i + 2] & 0xff : 0;
            result.append(BASE64_ALPHABET.charAt(first >>> 2));
            result.append(BASE64_ALPHABET.charAt(((first & 0x03) << 4) | (second >>> 4)));
            result.append(i + 1 < input.length ? BASE64_ALPHABET.charAt(((second & 0x0f) << 2) | (third >>> 6)) : '=');
            result.append(i + 2 < input.length ? BASE64_ALPHABET.charAt(third & 0x3f) : '=');
        }
        return result.toString();
    }
}
