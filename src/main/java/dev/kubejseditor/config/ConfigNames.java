package dev.kubejseditor.config;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** UI vocabulary only: never translates persisted config paths or values. */
public final class ConfigNames {
    private static final JsonObject LEXICON=load();
    private static JsonObject load(){
        try(var in=ConfigNames.class.getResourceAsStream("/assets/kubejs_editor/config_zh.json")){
            if(in==null)throw new IllegalStateException("Missing Chinese config vocabulary");
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException e){throw new ExceptionInInitializerError(e);}
    }
    public static String words(String value){
        var exact=LEXICON.getAsJsonObject("names").get(value);
        if(exact!=null)return exact.getAsString();
        String split=value.replaceAll("([A-Z]+)([A-Z][a-z])","$1 $2").replaceAll("([a-z0-9])([A-Z])","$1 $2");
        List<String> result=new ArrayList<>();
        for(String part:split.split("[^\\p{L}\\p{N}]+")){
            if(part.isEmpty())continue;
            JsonElement translated=LEXICON.getAsJsonObject("words").get(part.toLowerCase(Locale.ROOT));
            result.add(translated==null?part:translated.getAsString());
        }
        return String.join("",result);
    }
    public static String mod(String id){return switch(id){case "create"->"机械动力";case "aeronautics"->"机械动力：航空学";case "simulated"->"物理模拟";case "offroad"->"机械动力：越野";case "sable"->"航空学物理引擎";case "vintageimprovements","createvintageneoforged"->"机械动力：复古";case "createdieselgenerators"->"机械动力：柴油动力";case "create_dragons_plus"->"机械动力：龙＋";case "createaddition"->"机械动力：工艺与附加";case "create_new_age"->"机械动力：新生代";case "create_enchantment_industry"->"机械动力：附魔工业";default->words(id);};}
    public static String scope(String type){return switch(type){case "CLIENT"->"客户端（本机）";case "SERVER"->"服务端（当前世界）";case "COMMON"->"通用（当前实例）";default->type;};}
}
