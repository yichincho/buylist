package tw.yc.smartshopping;

public enum AiProvider {
    GEMINI("gemini"),
    DEEPSEEK("deepseek");

    public static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";
    private final String id;

    AiProvider(String id) { this.id = id; }

    public String id() { return id; }

    public static AiProvider resolve(String value) {
        return DEEPSEEK.id.equals(value) ? DEEPSEEK : GEMINI;
    }
}
