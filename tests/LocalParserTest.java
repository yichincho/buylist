package tw.yc.smartshopping;

import java.util.List;

public final class LocalParserTest {
    public static void main(String[] args) {
        List<LocalParser.ItemDraft> items = LocalParser.parse("牛奶、雞蛋\n衛生紙 2 包，醬油；香蕉");
        require(items.size() == 5, "mixed separators");
        require("衛生紙".equals(items.get(2).name), "quantity name");
        require("2 包".equals(items.get(2).quantity), "quantity value");
        require(LocalParser.parse("  \n，；").isEmpty(), "blank input");
        require("牛奶\n雞蛋".equals(LocalParser.appendVoice("牛奶", "雞蛋")), "voice append");
        System.out.println("LocalParserTest PASS");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
