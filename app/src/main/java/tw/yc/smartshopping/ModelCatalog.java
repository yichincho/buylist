package tw.yc.smartshopping;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
public final class ModelCatalog {
    private static final String URL = "https://raw.githubusercontent.com/yichincho/ai-models/main/models.json";
    private static final String CACHE = "ai_models_catalog";
    private ModelCatalog() {}
    private static JSONObject read(Context context) {
        String saved = context.getSharedPreferences(CACHE, 0).getString("json", null);
        try { if (saved != null) return new JSONObject(saved); } catch(Exception ignored) {}
        try { return new JSONObject("{\"providers\":{\"GEMINI\":{\"recommended\":\"gemini-3.1-flash-lite\",\"models\":[{\"id\":\"gemini-3.1-flash-lite\"}]},\"DEEPSEEK\":{\"recommended\":\"deepseek-flash\",\"models\":[{\"id\":\"deepseek-flash\"}]},\"NVIDIA\":{\"recommended\":\"deepseek-ai/deepseek-v4.1-flash\",\"models\":[{\"id\":\"deepseek-ai/deepseek-v4.1-flash\"}]}}}"); } catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static JSONObject provider(Context c, AiProvider p) { return read(c).optJSONObject("providers").optJSONObject(p.name()); }
    public static List<String> models(Context c, AiProvider p) {
        ArrayList<String> out = new ArrayList<>();
        JSONObject config = provider(c,p);
        if(config != null) { JSONArray a=config.optJSONArray("models"); if(a!=null) for(int i=0;i<a.length();i++) {JSONObject m=a.optJSONObject(i);if(m!=null && !m.optString("id").isEmpty())out.add(m.optString("id"));} }
        if(out.isEmpty())out.add(p==AiProvider.GEMINI?AiProvider.DEFAULT_MODEL:p==AiProvider.DEEPSEEK?DeepSeekClient.MODEL:"deepseek-ai/deepseek-v4.1-flash");
        return out;
    }
    public static String recommended(Context c,AiProvider p) { JSONObject x=provider(c,p);return x==null?models(c,p).get(0):x.optString("recommended",models(c,p).get(0)); }
    public static String selected(Context c,AiProvider p) {
        String saved=c.getSharedPreferences("shopping_settings",0).getString("ai_model_"+p.id(),null);
        return saved!=null && models(c,p).contains(saved)?saved:recommended(c,p);
    }
    public static String baseUrl(Context c,AiProvider p) {
        JSONObject x=provider(c,p);
        return x==null?(p==AiProvider.GEMINI?"https://generativelanguage.googleapis.com":p==AiProvider.NVIDIA?"https://integrate.api.nvidia.com/v1":"https://api.deepseek.com"):x.optString("baseUrl");
    }
    public static boolean refresh(Context c) {
        HttpURLConnection connection=null;
        try {
            connection=(HttpURLConnection)new URL(URL).openConnection();
            connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
            if(connection.getResponseCode()!=200)return false;
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;
            try(InputStream in=connection.getInputStream()){while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>262144)return false;}}
            String json=out.toString("UTF-8");JSONObject parsed=new JSONObject(json);
            if(parsed.optJSONObject("providers")==null)return false;
            c.getSharedPreferences(CACHE,0).edit().putString("json",json).apply();return true;
        }catch(Exception e){return false;}finally{if(connection!=null)connection.disconnect();}
    }
}
