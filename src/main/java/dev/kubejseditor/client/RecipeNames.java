package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.RecipeAdapters;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class RecipeNames {
    private static final Map<String,Map<String,String>> chinese=new HashMap<>();
    public static void invalidate(){chinese.clear();}
    public static String main(JsonObject data){
        if(data==null)return "未知配方";
        if(data.has("type")&&RecipeAdapters.get(data.get("type").getAsString()) instanceof dev.kubejseditor.recipe.SimpleRecipeAdapter simple)try{
            var output=simple.slots(data).stream().filter(dev.kubejseditor.recipe.RecipeCanvas.Slot::output).findFirst();if(output.isPresent()){var display=new JsonObject();display.add("result",output.get().get());return main(display);}
        }catch(Exception ignored){}
        if(data.has("type")&&dev.kubejseditor.recipe.VintageProcessing.dynamic(data.get("type").getAsString()))return dev.kubejseditor.recipe.VintageProcessing.dynamicTitle(data.get("type").getAsString());
        JsonElement result=data.has("result")?data.get("result"):data.has("results")&&!data.getAsJsonArray("results").isEmpty()?data.getAsJsonArray("results").get(0):null;
        if(result!=null&&!result.isJsonNull())try{
            String value=result.isJsonPrimitive()?result.getAsString():result.getAsJsonObject().has("id")?result.getAsJsonObject().get("id").getAsString():result.getAsJsonObject().get("item").getAsString();
            ResourceLocation id=ResourceLocation.parse(value);if(value.equals("minecraft:air"))return "未完成配方";
            String key;
            if(result.isJsonObject()&&result.getAsJsonObject().has("amount"))key=BuiltInRegistries.FLUID.get(id).getFluidType().getDescriptionId();
            else key=BuiltInRegistries.ITEM.get(id).getDescriptionId();
            Map<String,String> lang=chinese.computeIfAbsent(id.getNamespace(),ns->{
                Map<String,String> values=new HashMap<>();
                for(var resource:Minecraft.getInstance().getResourceManager().getResourceStack(ResourceLocation.fromNamespaceAndPath(ns,"lang/zh_cn.json")))try(var reader=resource.openAsReader()){JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->values.put(e.getKey(),e.getValue().getAsString()));}catch(Exception ignored){}
                return values;
            });
            String localized=net.minecraft.network.chat.Component.translatable(key).getString();return Minecraft.getInstance().getLanguageManager().getSelected().equals("zh_cn")?localized:lang.getOrDefault(key,localized);
        }catch(Exception ignored){}
        return "未完成配方";
    }
    public static String station(String type){var adapter=RecipeAdapters.get(type);return adapter==null?type:adapter instanceof dev.kubejseditor.recipe.RecipeTemplate||dev.kubejseditor.recipe.VintageProcessing.supports(type)?adapter.title():adapter.title().split(" · ")[0];}
    public static Map<String,String> numbered(Map<String,String> names){
        Map<String,Integer> counts=new HashMap<>(),indices=new HashMap<>();names.values().forEach(v->counts.merge(v,1,Integer::sum));
        Map<String,String> labels=new LinkedHashMap<>();names.keySet().stream().sorted().forEach(id->{String name=names.get(id);labels.put(id,counts.get(name)>1?name+"_"+indices.merge(name,1,Integer::sum):name);});return labels;
    }
}
