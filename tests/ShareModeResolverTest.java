package tw.yc.smartshopping;

public final class ShareModeResolverTest {
    public static void main(String[] args) {
        require(ShareModeResolver.Mode.OCR == ShareModeResolver.resolve(
                "tw.yc.smartshopping.OCR_SCREENSHOT", true), "OCR share");
        require(ShareModeResolver.Mode.REFERENCE == ShareModeResolver.resolve(
                "android.intent.action.SEND", true), "reference share");
        require(ShareModeResolver.Mode.NONE == ShareModeResolver.resolve(
                "tw.yc.smartshopping.OCR_SCREENSHOT", false), "non-image ignored");
        System.out.println("ShareModeResolverTest PASS");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
