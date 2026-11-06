package tw.yc.smartshopping;

import java.util.Arrays;
import java.util.List;

public final class LocalParserTest {
    public static void main(String[] args) {
        List<LocalParser.ItemDraft> items = LocalParser.parse("牛奶、雞蛋\n衛生紙 2 包，醬油；香蕉");
        require(items.size() == 5, "mixed separators");
        require("衛生紙".equals(items.get(2).name), "quantity name");
        require("2 包".equals(items.get(2).quantity), "quantity value");
        require(LocalParser.parse("  \n，；").isEmpty(), "blank input");
        require("牛奶\n雞蛋".equals(LocalParser.appendVoice("牛奶", "雞蛋")), "voice append");
        require("菜市場".equals(LocalParser.parse("菜市場青菜").get(0).store), "market store");
        require("未指定".equals(LocalParser.parse("醬油").get(0).store), "unassigned store");
        List<LocalParser.ItemDraft> sections = LocalParser.parse("全聯\n牛奶、雞蛋\n好市多：衛生紙 2 包\n家樂福\n醬油", Arrays.asList("家樂福"));
        require(sections.size() == 4, "store headings are not items");
        require("全聯".equals(sections.get(0).store) && "全聯".equals(sections.get(1).store), "section store");
        require("衛生紙".equals(sections.get(2).name) && "好市多".equals(sections.get(2).store), "inline heading");
        require("2 包".equals(sections.get(2).quantity), "inline heading quantity");
        require("家樂福".equals(sections.get(3).store), "custom store heading");
        require("好市多".equals(LocalParser.parse("costco: 牛奶").get(0).store), "costco heading");
        List<LocalParser.ItemDraft> chosen = LocalParser.withStore(LocalParser.parse("全聯牛奶、醬油"), "菜市場");
        require("菜市場".equals(chosen.get(0).store) && "菜市場".equals(chosen.get(1).store), "chosen store");
        require("全聯".equals(LocalParser.withStore(LocalParser.parse("全聯牛奶"), null).get(0).store), "auto store");
        System.out.println("LocalParserTest PASS");
    }

    private static void require(boolean value, String name) {
        if (!value) throw new AssertionError(name);
    }
}
