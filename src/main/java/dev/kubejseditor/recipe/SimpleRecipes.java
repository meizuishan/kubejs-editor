package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.storage.FileTransaction;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Discovery is always enabled. The old preference is retained only as a recognized legacy path. */
public final class SimpleRecipes {
    public static final String FILE="kubejs/config/kubejs_editor/simple-recipes.json";
    public record JeiEvidence(String category,String title,int inputs,int outputs,int fluidInputs,int fluidOutputs){}
    private static Path root;
    private static final Map<String,SimpleRecipeAdapter> discovered=new LinkedHashMap<>();
    private static final Map<String,JeiEvidence> evidence=new HashMap<>();
    public static boolean enabled(){return true;}
    public static synchronized void load(Path directory)throws IOException{
        Path next=directory.toAbsolutePath().normalize();if(next.equals(root))return;clear();root=next;
    }
    /** Binary-compatible legacy API; obsolete off settings never disable discovery. */
    public static void setEnabled(Path directory,boolean value)throws IOException{load(directory);}
    public static void clear(){discovered.forEach(RecipeAdapters::unregister);discovered.clear();evidence.clear();}
    public static void yieldToTemplate(String type){var prior=discovered.remove(type);if(prior!=null)RecipeAdapters.unregister(type,prior);}
    public static SimpleRecipeAdapter discover(JsonObject data){
        String type=data.get("type").getAsString();RecipeAdapter existing=RecipeAdapters.get(type);if(existing!=null)return existing instanceof SimpleRecipeAdapter simple?simple:null;
        var adapter=new SimpleRecipeAdapter(data);var problems=adapter.validate(data);if(!problems.isEmpty())throw new IllegalArgumentException(String.join("；",problems));
        RecipeAdapters.register(adapter);discovered.put(type,adapter);return adapter;
    }
    public static void restore(RecipeDraft draft){if(draft.simple&&draft.data!=null&&!draft.operation.equals("remove"))discover(draft.data);}
    public static void record(String recipeId,JeiEvidence hint){evidence.put(recipeId,hint);}
    public static void clearJeiEvidence(){evidence.clear();}
    public static JeiEvidence evidence(String recipeId){return evidence.get(recipeId);}
    public static String boundary(String recipeId,JsonObject data){
        var adapter=RecipeAdapters.get(data.get("type").getAsString());if(!(adapter instanceof SimpleRecipeAdapter simple))return null;
        var errors=simple.validate(data);if(!errors.isEmpty())return String.join("；",errors);
        // Display multiplicity is not native field cardinality; per-slot binding handles ambiguity.
        return null;
    }
    public static String description(String recipeId,JsonObject data){
        var lines=new ArrayList<String>();lines.add("配方字段：原始 JSON 为准，修改后通过原生格式校验。");
        var hint=evidence.get(recipeId);lines.add(hint==null?"尚无当前 JEI 槽位证据；原生 Codec 仍会检查每次修改。":"JEI 分类："+hint.title+" / "+hint.category+"；输入 "+hint.inputs+"，产物 "+hint.outputs);
        try{for(var binding:SimpleRecipeAdapter.bindings(data))lines.add((binding.output()?"产物 ":"输入 ")+binding.pointer());}catch(Exception e){lines.add(e.getMessage());}
        lines.add("能量、时间等仅编辑配方本身的已存在字段；机器配置或隐藏条件需要专用适配。");return String.join("\n",lines);
    }
    private SimpleRecipes(){}
}
