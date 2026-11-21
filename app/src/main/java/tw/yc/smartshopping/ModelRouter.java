package tw.yc.smartshopping;
import android.content.Context;
public final class ModelRouter {
    private ModelRouter() {}
    public static DeepSeekClient.Result organize(Context c,AiProvider p,String key,String text) {
        String model=ModelCatalog.selected(c,p);
        if(p==AiProvider.GEMINI)return new GeminiClient(ModelCatalog.baseUrl(c,p),model).organize(key,text);
        return DeepSeekClient.organize(key,text,model,ModelCatalog.baseUrl(c,p));
    }
    public static DeepSeekClient.Result organizeImage(Context c,AiProvider p,String key,byte[] jpeg) {
        String model=ModelCatalog.selected(c,p);
        if(p==AiProvider.GEMINI)return new GeminiClient(ModelCatalog.baseUrl(c,p),model).organizeImage(key,jpeg);
        if(p==AiProvider.DEEPSEEK)return new DeepSeekClient.Result(new java.util.ArrayList<>(),true,"DeepSeek 此模型未確認支援圖片，請選擇 Gemini 辨識");
        return DeepSeekClient.organizeImage(key,jpeg,model,ModelCatalog.baseUrl(c,p));
    }
    public static String test(Context c,AiProvider p,String key) {
        DeepSeekClient.Result r=organize(c,p,key,"牛奶");return r.fallback?r.message:"連線成功";
    }
}
