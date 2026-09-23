package tw.yc.smartshopping;

public final class ShareModeResolver {
    public static final String ACTION_OCR_SCREENSHOT = "tw.yc.smartshopping.OCR_SCREENSHOT";
    public enum Mode { OCR, REFERENCE, NONE }
    private ShareModeResolver() {}
    public static Mode resolve(String action, boolean isImage) {
        if (!isImage) return Mode.NONE;
        if (ACTION_OCR_SCREENSHOT.equals(action)) return Mode.OCR;
        if ("android.intent.action.SEND".equals(action) || "android.intent.action.SEND_MULTIPLE".equals(action)) return Mode.REFERENCE;
        return Mode.NONE;
    }
}

