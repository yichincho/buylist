package tw.yc.smartshopping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeminiResponseParser {
    private GeminiResponseParser() {}

    public static DeepSeekClient.Result parse(String body, String originalText) {
        try {
            Object decoded = new JsonReader(body).parse();
            if (!(decoded instanceof Map)) return fallback(originalText);
            Object candidatesValue = ((Map<?, ?>) decoded).get("candidates");
            if (!(candidatesValue instanceof List) || ((List<?>) candidatesValue).isEmpty()) return fallback(originalText);

            Object candidate = ((List<?>) candidatesValue).get(0);
            if (!(candidate instanceof Map)) return fallback(originalText);
            Object content = ((Map<?, ?>) candidate).get("content");
            if (!(content instanceof Map)) return fallback(originalText);
            Object partsValue = ((Map<?, ?>) content).get("parts");
            if (!(partsValue instanceof List)) return fallback(originalText);

            StringBuilder text = new StringBuilder();
            for (Object part : (List<?>) partsValue) {
                if (!(part instanceof Map)) continue;
                Object partText = ((Map<?, ?>) part).get("text");
                if (partText instanceof String) text.append(partText);
            }
            if (text.toString().trim().isEmpty()) return fallback(originalText);

            Object itemResponse = new JsonReader(text.toString()).parse();
            if (!(itemResponse instanceof Map)) return fallback(originalText);
            Object rowsValue = ((Map<?, ?>) itemResponse).get("items");
            if (!(rowsValue instanceof List)) return fallback(originalText);

            List<LocalParser.ItemDraft> items = new ArrayList<>();
            List<?> rows = (List<?>) rowsValue;
            for (int index = 0; index < rows.size(); index++) {
                Object row = rows.get(index);
                if (!(row instanceof Map)) continue;
                Map<?, ?> fields = (Map<?, ?>) row;
                String name = stringValue(fields.get("name"));
                if (name.isEmpty()) continue;
                items.add(new LocalParser.ItemDraft(
                        name,
                        stringValue(fields.get("quantity")),
                        nonempty(fields.get("store"), "未指定"),
                        nonempty(fields.get("category"), "其他"),
                        index));
            }
            if (items.isEmpty()) return fallback(originalText);
            return new DeepSeekClient.Result(items, false, "Gemini 整理完成");
        } catch (RuntimeException error) {
            return fallback(originalText);
        }
    }

    private static DeepSeekClient.Result fallback(String originalText) {
        return DeepSeekClient.Result.fallback("AI 回應格式無效，已改用本機整理", originalText);
    }

    private static String nonempty(Object value, String defaultValue) {
        String text = stringValue(value);
        return text.isEmpty() ? defaultValue : text;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** Small strict JSON decoder for the two response objects used here. */
    private static final class JsonReader {
        private final String source;
        private int position;
        private int depth;

        JsonReader(String source) {
            this.source = source == null ? "" : source;
        }

        Object parse() {
            Object value = readValue();
            skipWhitespace();
            if (position != source.length()) throw error("trailing content");
            return value;
        }

        private Object readValue() {
            skipWhitespace();
            if (position >= source.length()) throw error("expected value");
            char c = source.charAt(position);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't') { readLiteral("true"); return Boolean.TRUE; }
            if (c == 'f') { readLiteral("false"); return Boolean.FALSE; }
            if (c == 'n') { readLiteral("null"); return null; }
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            throw error("unexpected character");
        }

        private Map<String, Object> readObject() {
            enter();
            position++;
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) { leave(); return result; }
            while (true) {
                skipWhitespace();
                if (position >= source.length() || source.charAt(position) != '"') throw error("expected object key");
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (take('}')) { leave(); return result; }
                expect(',');
            }
        }

        private List<Object> readArray() {
            enter();
            position++;
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (take(']')) { leave(); return result; }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (take(']')) { leave(); return result; }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') return result.toString();
                if (c < 0x20) throw error("unescaped control character");
                if (c != '\\') { result.append(c); continue; }
                if (position >= source.length()) throw error("incomplete escape");
                char escaped = source.charAt(position++);
                switch (escaped) {
                    case '"': result.append('"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u': result.append(readUnicode()); break;
                    default: throw error("invalid escape");
                }
            }
            throw error("unterminated string");
        }

        private char readUnicode() {
            if (position + 4 > source.length()) throw error("incomplete unicode escape");
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(source.charAt(position++), 16);
                if (digit < 0) throw error("invalid unicode escape");
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Number readNumber() {
            int start = position;
            if (take('-') && position >= source.length()) throw error("incomplete number");
            if (take('0')) {
                if (position < source.length() && Character.isDigit(source.charAt(position))) throw error("leading zero");
            } else {
                requireDigits();
            }
            boolean decimal = false;
            if (take('.')) { decimal = true; requireDigits(); }
            if (take('e') || take('E')) {
                decimal = true;
                if (!take('+')) take('-');
                requireDigits();
            }
            String number = source.substring(start, position);
            try { return decimal ? Double.valueOf(number) : Long.valueOf(number); }
            catch (NumberFormatException error) { throw error("invalid number"); }
        }

        private void requireDigits() {
            int start = position;
            while (position < source.length() && source.charAt(position) >= '0' && source.charAt(position) <= '9') position++;
            if (position == start) throw error("expected digit");
        }

        private void readLiteral(String value) {
            if (!source.regionMatches(position, value, 0, value.length())) throw error("invalid literal");
            position += value.length();
        }

        private void enter() {
            if (++depth > 64) throw error("JSON nesting too deep");
        }

        private void leave() { depth--; }

        private void skipWhitespace() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private boolean take(char c) {
            if (position < source.length() && source.charAt(position) == c) { position++; return true; }
            return false;
        }

        private void expect(char c) {
            if (!take(c)) throw error("expected '" + c + "'");
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + position);
        }
    }
}
