package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.storage.FileTransaction;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Per-instance discovery preference; saved recipe annotations remain usable when discovery is disabled. */
public final class SimpleRecipes {
    public static final String FILE="kubejs/config/kubejs_editor/simple-recipes.json";
    public record JeiEvidence(String category,String title,int inputs,int outputs,int fluidInputs,int fluidOutputs){}
    private static Path root;
    private static boolean enabled;
    private static final Map<String,SimpleRecipeAdapter> discovered=new LinkedHashMap<>();
    private static final Map<String,JeiEvidence> evidence=new HashMap<>();
    public static boolean enabled(){return enabled;}
    public static synchronized void load(Path directory)throws IOException{
        Path next=directory.toAbsolutePath().normalize();if(next.equals(root))return;clear();root=next;enabled=false;
        Path file=next.resolve(FILE);if(Files.exists(file)){var data=JsonParser.parseString(Files.readString(file)).getAsJsonObject();if(!data.has("enabled")||!data.get("enabled").isJsonPrimitive()||!data.get("enabled").getAsJsonPrimitive().isBoolean())throw new IOException("简单配方设置损坏，原文件保留");enabled=data.get("enabled").getAsBoolean();}
    }
    public static void setEnabled(Path directory,boolean value)throws IOException{load(directory);FileTransaction.atomicWrite(directory.resolve(FILE),("{\"schemaVersion\":1,\"enabled\":"+value+"}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));enabled=value;}
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
        var hint=evidence.get(recipeId);if(hint!=null){
            var slots=simple.slots(data);long in=slots.stream().filter(s->!s.output()).count(),out=slots.stream().filter(RecipeCanvas.Slot::output).count();
            long fluidsIn=slots.stream().filter(s->!s.output()&&s.fluid()).count(),fluidsOut=slots.stream().filter(s->s.output()&&s.fluid()).count();
            if(in!=hint.inputs||out!=hint.outputs||fluidsIn!=hint.fluidInputs||fluidsOut!=hint.fluidOutputs)return "JEI 槽位与原生字段未完全匹配，保留只读（原生 "+in+" 输入/"+out+" 产物；JEI "+hint.inputs+"/"+hint.outputs+"）";
        }return null;
    }
    public static String description(String recipeId,JsonObject data){
        var lines=new ArrayList<String>();lines.add("简单配方适配（实验）：原始 JSON 为准；只替换现有槽位，不增删槽位。");
        var hint=evidence.get(recipeId);lines.add(hint==null?"尚无当前 JEI 槽位证据；原生 Codec 仍会检查每次修改。":"JEI 分类："+hint.title+" / "+hint.category+"；输入 "+hint.inputs+"，产物 "+hint.outputs);
        try{for(var binding:SimpleRecipeAdapter.bindings(data))lines.add((binding.output()?"产物 ":"输入 ")+binding.pointer());}catch(Exception e){lines.add(e.getMessage());}
        lines.add("能量、时间等仅编辑配方本身的已存在字段；机器配置或隐藏条件需要专用适配。");return String.join("\n",lines);
    }
    private SimpleRecipes(){}
}
