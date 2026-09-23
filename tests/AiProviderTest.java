package tw.yc.smartshopping;

public final class AiProviderTest {
    public static void main(String[] args) {
        assertEquals(AiProvider.GEMINI, AiProvider.resolve(null));
        assertEquals(AiProvider.GEMINI, AiProvider.resolve(""));
        assertEquals(AiProvider.GEMINI, AiProvider.resolve("unknown-provider"));
        assertEquals(AiProvider.DEEPSEEK, AiProvider.resolve("deepseek"));
        assertEquals(AiProvider.GEMINI, AiProvider.resolve("DeepSeek"));
        System.out.println("AiProviderTest PASS");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + " but was " + actual);
    }
}
