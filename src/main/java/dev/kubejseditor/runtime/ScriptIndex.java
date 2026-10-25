package dev.kubejseditor.runtime;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.*;
import dev.latvian.mods.kubejs.recipe.*;
import dev.latvian.mods.kubejs.core.RecipeManagerKJS;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Observes KubeJS public plugin callbacks. No script execution or recipe overrides here. */
@net.neoforged.fml.common.EventBusSubscriber(modid="kubejs_editor")
public final class ScriptIndex implements KubeJSPlugin {
    public static final Map<String,String> loaded=new ConcurrentHashMap<>();
    private static volatile RecipesKubeEvent recipes;
    private static volatile Map<String,String> sourceIndex;
    public static dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaStorage schemas(){var event=recipes;return event==null?null:event.recipeSchemaStorage;}
    public static net.minecraft.resources.RegistryOps<JsonElement> recipeOps(){var event=recipes;return event==null?null:event.ops.json();}
    public static volatile long serverGeneration,clientGeneration,startupGeneration,completedGeneration;
    @net.neoforged.bus.api.SubscribeEvent public static void synced(net.neoforged.neoforge.event.OnDatapackSyncEvent event){sourceIndex=null;completedGeneration=serverGeneration;}
    @Override public void afterScriptsLoaded(ScriptManager manager){
        String stage=manager.scriptType.isServer()?"server_scripts":manager.scriptType.isClient()?"client_scripts":"startup_scripts";
        loaded.keySet().removeIf(k->k.startsWith("kubejs/"+stage+"/"));
        for(var pack:manager.packs.values())for(var file:pack.scripts){
            String path=file.info.path.toString().replace('\\','/');int i=path.lastIndexOf("/"+stage+"/");
            if(i>=0)loaded.put("kubejs"+path.substring(i),file.skipLoading()==null?"已加载":"已跳过："+file.skipLoading());
        }
        // With zero server scripts KubeJS skips recipe events entirely. Never expose the
        // previous reload's additions/removals after the final generated file is revoked.
        if(manager.scriptType.isServer()){recipes=null;sourceIndex=null;serverGeneration++;}else if(manager.scriptType.isClient())clientGeneration++;else startupGeneration++;
    }
    @Override public void beforeRecipeLoading(RecipesKubeEvent event,RecipeManagerKJS manager,Map<ResourceLocation,JsonElement> data){recipes=event;sourceIndex=null;CompatibilityRecipes.invalidateSchemas();}
    public static Map<String,String> recipeSources(){
        var cached=sourceIndex;if(cached!=null)return cached;
        Map<String,String> sources=new LinkedHashMap<>();RecipesKubeEvent event=recipes;if(event==null)return sources;
        for(KubeRecipe r:event.originalRecipes.values())if(r.hasChanged()||r.removed)put(sources,r);
        for(KubeRecipe r:event.addedRecipes)put(sources,r);
        for(KubeRecipe r:event.removedRecipes)put(sources,r);
        sourceIndex=Collections.unmodifiableMap(sources);return sourceIndex;
    }
    private static void put(Map<String,String> out,KubeRecipe r){String source=r.sourceLine==null||r.sourceLine.isUnknown()?"KJS 修改 · 来源行未识别":r.sourceLine.toString();out.put(r.getOrCreateId().toString(),source);}
    public static List<String> errors(ConsoleJS console){synchronized(console.errors){return console.errors.stream().map(e->e.getText()+" "+e.sourceLines).toList();}}
}
