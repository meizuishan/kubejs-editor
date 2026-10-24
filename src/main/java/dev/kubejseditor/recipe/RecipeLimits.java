package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Entry counts, independent of stack sizes. Verified against Create 6.0.10. */
public record RecipeLimits(int inputs,int outputs,int fluidInputs,int fluidOutputs) {
    public static RecipeLimits forType(String type){
        if(VintageProcessing.supports(type))return VintageProcessing.get(type).limits();
        if(RecipeAdapters.get(type) instanceof RecipeTemplate template)return template.limits();
        return switch(type){
            case "create:crushing" -> new RecipeLimits(1,7,0,0);
            case "create:milling","create:cutting" -> new RecipeLimits(1,4,0,0);
            case "create:mixing","create:compacting" -> new RecipeLimits(64,4,2,2);
            case "create:pressing" -> new RecipeLimits(1,2,0,0);
            case "create:deploying","create:item_application" -> new RecipeLimits(2,4,0,0);
            case "create:splashing","create:haunting","create_dragons_plus:freezing","create_dragons_plus:ending","create_dragons_plus:sanding" -> new RecipeLimits(1,12,0,0);
            case "create:sandpaper_polishing" -> new RecipeLimits(1,1,0,0);
            case "create:filling" -> new RecipeLimits(1,1,1,0);
            case "create:emptying" -> new RecipeLimits(1,1,0,1);
            case "create:sequenced_assembly" -> new RecipeLimits(1,Integer.MAX_VALUE,0,0);
            case "create:mechanical_crafting" -> new RecipeLimits(81,1,0,0);
            case "minecraft:smithing_transform" -> new RecipeLimits(3,1,0,0);
            case "minecraft:crafting_shaped","minecraft:crafting_shapeless" -> new RecipeLimits(9,1,0,0);
            default -> new RecipeLimits(1,1,0,0);
        };
    }
    public static boolean fluid(JsonElement e){return e.isJsonObject()&&(e.getAsJsonObject().has("amount")||e.getAsJsonObject().has("fluid")||e.getAsJsonObject().has("type")&&e.getAsJsonObject().get("type").getAsString().matches("neoforge:(single|tag)"));}
    public int count(JsonObject data,boolean output,boolean fluid){
        String key=output?"results":"ingredients";if(!data.has(key))return 0;
        int n=0;for(JsonElement e:data.getAsJsonArray(key))if(fluid(e)==fluid)n++;return n;
    }
    public int max(boolean output,boolean fluid){return fluid?(output?fluidOutputs:fluidInputs):(output?outputs:inputs);}
    public boolean canAdd(JsonObject data,boolean output,boolean fluid){return count(data,output,fluid)<max(output,fluid);}
    public List<String> validate(JsonObject data){
        List<String> errors=new ArrayList<>();
        for(boolean output:new boolean[]{false,true})for(boolean fluid:new boolean[]{false,true}){
            int n=count(data,output,fluid),max=max(output,fluid);if(n>max)errors.add((output?"/results":"/ingredients")+" "+(fluid?"流体":"物品")+(output?"产出":"输入")+"条目为 "+n+"，最多允许 "+max+" 个（与单条数量无关）");
        }return errors;
    }
}
