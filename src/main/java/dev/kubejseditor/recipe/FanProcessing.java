package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Fan modes retain their real serializer IDs. Addons may register further adapters/modes. */
public final class FanProcessing {
    public record Mode(String type,String name,String condition,String category) {}
    private static final Map<String,Mode> MODES=new LinkedHashMap<>();
    static {
        register(new Mode("create:splashing","水洗","气流穿过水",""));
        register(new Mode("create:haunting","缠魂","气流穿过灵魂火",""));
        register(new Mode("create_dragons_plus:freezing","冷冻（龙+）","气流穿过细雪","FanFreezingCategory"));
        register(new Mode("create_dragons_plus:ending","终结（龙+）","气流穿过龙首或龙息","FanEndingCategory"));
        register(new Mode("create_dragons_plus:sanding","喷砂（龙+）","气流穿过喷砂催化方块；需整合包提供相应标签","FanSandingCategory"));
    }
    public static void register(Mode mode){MODES.put(mode.type(),mode);}
    public static Collection<Mode> all(){return Collections.unmodifiableCollection(MODES.values());}
    public static Mode get(String type){return MODES.get(type);}
    public static boolean supports(String type){return MODES.containsKey(type);}
    public static boolean createType(String type){return type.startsWith("create:")||type.startsWith(VintageProcessing.PREFIX)||supports(type);}
    /** All supported modes have identical item/probability semantics; retain extra fields for round trips. */
    public static void switchMode(JsonObject data,String type){
        if(!supports(data.get("type").getAsString())||!supports(type))throw new IllegalArgumentException("不支持此鼓风机加工方式");
        data.addProperty("type",type);
    }
    private FanProcessing(){}
}
