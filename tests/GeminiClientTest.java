package tw.yc.smartshopping;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class GeminiClientTest {
    private static final String MODEL_PATH = "/v1beta/models/gemini-3.1-flash-lite:generateContent";
    private static final String VALID_ITEMS = "{\"items\":[{\"name\":\"牛奶\",\"quantity\":\"2瓶\"}]}";

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger responseCode = new AtomicInteger(200);
        server.createContext("/", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(readUtf8(exchange.getRequestBody()));
            byte[] response = responseCode.get() == 200
                    ? responseWithText(VALID_ITEMS).getBytes(StandardCharsets.UTF_8)
                    : "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode.get(), response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiClient client = new GeminiClient(baseUrl);
            testTextRequest(client, method, path, query, apiKey, body);
            testImageRequest(client, path, apiKey, body);
            testValidationRequest(client, path, body);
            testRateLimitFallback(client, responseCode, apiKey, body);
            System.out.println("GeminiClientTest PASS");
        } finally {
            server.stop(0);
        }
    }

    private static void testTextRequest(GeminiClient client, AtomicReference<String> method,
            AtomicReference<String> path, AtomicReference<String> query,
            AtomicReference<String> apiKey, AtomicReference<String> body) {
        DeepSeekClient.Result result = client.organize("test-api-key", "牛奶");
        assertEquals("POST", method.get());
        assertEquals(MODEL_PATH, path.get());
        assertEquals(null, query.get());
        assertEquals("test-api-key", apiKey.get());
        assertContains(body.get(), "\"responseMimeType\":\"application/json\"");
        assertContains(body.get(), "整理我貼上的需求：牛奶");
        assertFalse(body.get().contains("inlineData"));
        assertResponseSchema(body.get());
        assertFalse(body.get().contains("test-api-key"));
        assertFalse(result.fallback);
        assertEquals("牛奶", result.items.get(0).name);
        assertEquals("2瓶", result.items.get(0).quantity);
    }

    private static void testImageRequest(GeminiClient client, AtomicReference<String> path,
            AtomicReference<String> apiKey, AtomicReference<String> body) {
        byte[] jpeg = new byte[]{1, 2, 3, 4};
        DeepSeekClient.Result result = client.organizeImage("image-api-key", jpeg);
        assertEquals(MODEL_PATH, path.get());
        assertEquals("image-api-key", apiKey.get());
        assertContains(body.get(), "\"inlineData\":{\"mimeType\":\"image/jpeg\"");
        assertContains(body.get(), Base64.getEncoder().encodeToString(jpeg));
        assertResponseSchema(body.get());
        assertFalse(body.get().contains("image-api-key"));
        assertFalse(result.fallback);
        assertEquals("牛奶", result.items.get(0).name);
    }

    private static void testValidationRequest(GeminiClient client, AtomicReference<String> path,
            AtomicReference<String> body) {
        assertEquals("連線成功", client.test("validation-key"));
        assertEquals(MODEL_PATH, path.get());
        assertContains(body.get(), "牛奶");
    }

    private static void testRateLimitFallback(GeminiClient client, AtomicInteger responseCode,
            AtomicReference<String> apiKey, AtomicReference<String> body) {
        responseCode.set(429);
        DeepSeekClient.Result result = client.organize("rate-limit-key", "雞蛋");
        assertTrue(result.fallback);
        assertEquals("雞蛋", result.items.get(0).name);
        assertEquals("rate-limit-key", apiKey.get());
        assertFalse(body.get().contains("rate-limit-key"));
    }

    private static String responseWithText(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]}}]}";
    }

    private static String quote(String value) {
        StringBuilder output = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') output.append('\\').append(c);
            else if (c == '\n') output.append("\\n");
            else if (c == '\r') output.append("\\r");
            else if (c == '\t') output.append("\\t");
            else output.append(c);
        }
        return output.append('"').toString();
    }

    private static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void assertContains(String text, String part) {
        if (text == null || !text.contains(part)) throw new AssertionError("expected <" + text + "> to contain <" + part + ">");
    }

    private static void assertResponseSchema(String json) {
        Map<String, Object> request = asObject(new RequestJson(json).parse());
        Map<String, Object> generationConfig = asObject(request.get("generationConfig"));
        Map<String, Object> schema = asObject(generationConfig.get("responseSchema"));
        assertEquals("OBJECT", schema.get("type"));
        assertEquals(java.util.Arrays.asList("items"), schema.get("required"));

        Map<String, Object> properties = asObject(schema.get("properties"));
        assertFalse(properties.containsKey("required"));
        Map<String, Object> items = asObject(properties.get("items"));
        assertEquals("ARRAY", items.get("type"));
        Map<String, Object> itemSchema = asObject(items.get("items"));
        assertEquals("OBJECT", itemSchema.get("type"));
        assertEquals(java.util.Arrays.asList("name", "quantity", "store", "category"), itemSchema.get("required"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map)) throw new AssertionError("expected JSON object, got " + value);
        return (Map<String, Object>) value;
    }

    private static final class RequestJson {
        private final String source;
        private int position;

        RequestJson(String source) { this.source = source; }

        Object parse() {
            Object value = readValue();
            skipWhitespace();
            if (position != source.length()) throw error("trailing JSON content");
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (position >= source.length()) throw error("expected value");
            char next = source.charAt(position);
            if (next == '{') return readObject();
            if (next == '[') return readArray();
            if (next == '"') return readString();
            if (source.startsWith("true", position)) { position += 4; return Boolean.TRUE; }
            if (source.startsWith("false", position)) { position += 5; return Boolean.FALSE; }
            if (source.startsWith("null", position)) { position += 4; return null; }
            throw error("unsupported JSON value");
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) return object;
            do {
                skipWhitespace();
                if (position >= source.length() || source.charAt(position) != '"') throw error("expected object key");
                String key = readString();
                skipWhitespace();
                expect(':');
                object.put(key, readValue());
                skipWhitespace();
                if (take('}')) return object;
                expect(',');
            } while (true);
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (take(']')) return array;
            do {
                array.add(readValue());
                skipWhitespace();
                if (take(']')) return array;
                expect(',');
            } while (true);
        }

        private String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char next = source.charAt(position++);
                if (next == '"') return value.toString();
                if (next != '\\') { value.append(next); continue; }
                if (position >= source.length()) throw error("incomplete escape");
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '"': case '\\': case '/': value.append(escaped); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (position + 4 > source.length()) throw error("incomplete unicode escape");
                        value.append((char) Integer.parseInt(source.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default: throw error("invalid escape");
                }
            }
            throw error("unterminated string");
        }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private boolean take(char expected) {
            if (position < source.length() && source.charAt(position) == expected) { position++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw error("expected " + expected);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + position);
        }
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("expected true"); }
    private static void assertFalse(boolean value) { if (value) throw new AssertionError("expected false"); }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
