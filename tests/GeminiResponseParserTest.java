package tw.yc.smartshopping;

public final class GeminiResponseParserTest {
    public static void main(String[] args) {
        testCandidateTextPartsBecomeItems();
        testMissingCandidateFallsBackToLocalParser();
        testEmptyCandidateTextFallsBackToLocalParser();
        testMalformedItemJsonFallsBackToLocalParser();
        System.out.println("GeminiResponseParserTest PASS");
    }

    private static void testCandidateTextPartsBecomeItems() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"{\\\"items\\\":[\"},"
                + "{\"text\":\"{\\\"name\\\":\\\"牛奶\\\",\\\"quantity\\\":\\\"2瓶\\\"},{\\\"name\\\":\\\" \\\"}]}\"}"
                + "]}}]}";
        DeepSeekClient.Result result = GeminiResponseParser.parse(body, "本機備援");

        assertFalse(result.fallback);
        assertEquals(1, result.items.size());
        assertEquals("牛奶", result.items.get(0).name);
        assertEquals("2瓶", result.items.get(0).quantity);
        assertEquals("未指定", result.items.get(0).store);
        assertEquals("其他", result.items.get(0).category);
    }

    private static void testMissingCandidateFallsBackToLocalParser() {
        DeepSeekClient.Result result = GeminiResponseParser.parse("{\"error\":{}}", "雞蛋,豆腐");
        assertTrue(result.fallback);
        assertEquals(2, result.items.size());
        assertEquals("雞蛋", result.items.get(0).name);
        assertEquals("豆腐", result.items.get(1).name);
    }

    private static void testEmptyCandidateTextFallsBackToLocalParser() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  \"}]}}]}";
        DeepSeekClient.Result result = GeminiResponseParser.parse(body, "蘋果");
        assertTrue(result.fallback);
        assertEquals("蘋果", result.items.get(0).name);
    }

    private static void testMalformedItemJsonFallsBackToLocalParser() {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not json\"}]}}]}";
        DeepSeekClient.Result result = GeminiResponseParser.parse(body, "香蕉");
        assertTrue(result.fallback);
        assertEquals("香蕉", result.items.get(0).name);
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("expected true");
    }

    private static void assertFalse(boolean value) {
        if (value) throw new AssertionError("expected false");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }
}
