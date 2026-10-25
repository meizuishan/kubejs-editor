package dev.kubejseditor.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.export.GenericFormatter;
import dev.kubejseditor.recipe.SourceConversion;
import dev.kubejseditor.storage.ProjectStore;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import java.io.*;
import java.util.*;

/** Data-driven provenance for explicit conversions; matching uses the full native display Codec. */
public final class ConvertedRecipeSources {
    private static final List<SourceConversion> rules=new ArrayList<>();
    private static boolean initialized;
    static {
        try(var stream=ConvertedRecipeSources.class.getResourceAsStream("/kubejs_editor/source-conversions.json")){
            if(stream==null)throw new IOException("缺少配方来源转换定义");
            for(var rule:ProjectStore.JSON.fromJson(new InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8),SourceConversion[].class))register(rule);
        }catch(IOException e){throw new ExceptionInInitializerError(e);}initialized=true;
    }
    /** Extensions may register other explicit conversions without a dedicated editor screen. */
    public static void register(SourceConversion rule){rule.check();rules.removeIf(r->r.category.equals(rule.category)&&r.sourceType.equals(rule.sourceType));rules.add(rule);if(initialized){JeiRecipeSources.clear();CompatJeiLayouts.reset();}}
    public static JsonObject preview(String category,JsonObject source){return rules.stream().filter(r->r.category.equals(category)&&r.sourceType.equals(source.get("type").getAsString())).findFirst().map(r->r.project(source)).orElse(source);}
    public static boolean supports(String category,String nativeType){return rules.stream().anyMatch(r->r.category.equals(category)&&r.sourceType.equals(nativeType));}
    @SuppressWarnings({"rawtypes","unchecked"}) public static JeiRecipeSources.Source resolve(IRecipeLayoutDrawable<?> layout,RecipeManager manager){
        if(!(layout.getRecipe() instanceof RecipeHolder<?> display))return null;
        String category=layout.getRecipeCategory().getRecipeType().getUid().toString();
        for(var rule:rules){
            if(!rule.category.equals(category)||rule.sourcePath(display.id().toString())==null||!Objects.equals(BuiltInRegistries.RECIPE_SERIALIZER.getKey(display.value().getSerializer()),ResourceLocation.parse(rule.displayType)))continue;
            var mc=Minecraft.getInstance();var registries=mc.hasSingleplayerServer()?mc.getSingleplayerServer().registryAccess():mc.level.registryAccess();var ops=registries.createSerializationContext(JsonOps.INSTANCE);
            com.mojang.serialization.Codec codec=display.value().getSerializer().codec().codec();
            JsonElement expected=(JsonElement)codec.encodeStart(ops,display.value()).getOrThrow();
            var candidates=new ArrayList<ResourceLocation>();
            for(var holder:JeiRecipeSources.byPath(rule.sourcePath(display.id().toString()))){
                String type=Objects.toString(BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer()),"");
                if(!rule.candidate(display.id().toString(),holder.id().toString(),type))continue;
                try{var source=GenericFormatter.encodeRecipe(holder).getAsJsonObject();source.addProperty("type",type);var projected=rule.project(source);Object decoded=codec.parse(ops,projected).getOrThrow();if(expected.equals(codec.encodeStart(ops,decoded).getOrThrow()))candidates.add(holder.id());}
                catch(RuntimeException ignored){/* A path hint alone must never authorize an unrelated source. */}
            }
            String notice="这是转换兼容配方，编辑的是原始来源；修改会影响使用该来源的普通机器及兼容机器。";
            if(candidates.size()==1)return new JeiRecipeSources.Source(candidates.getFirst(),null,List.of(),notice);
            return new JeiRecipeSources.Source(null,candidates.isEmpty()?"转换配方未找到通过完整原生格式核对的来源；保留只读":"转换配方匹配到多个来源，请确认原始配方。"+notice,List.copyOf(candidates),notice);
        }
        return null;
    }
    private ConvertedRecipeSources(){}
}
