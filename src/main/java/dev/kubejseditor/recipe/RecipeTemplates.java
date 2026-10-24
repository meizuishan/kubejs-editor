package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BiFunction;

/** Bad files are isolated; neither built-in adapters nor draft data are overwritten. */
public final class RecipeTemplates {
    public static final String DIRECTORY="kubejs/config/kubejs_editor/templates";
    private static final Map<String,RecipeTemplate> loaded=new LinkedHashMap<>();
    private static final List<String> diagnostics=new ArrayList<>();
    private static int revision;
    public static int revision(){return revision;}
    public static synchronized List<String> load(Path root,BiFunction<String,String,String> requirement){
        loaded.forEach(RecipeAdapters::unregister);loaded.clear();diagnostics.clear();revision++;
        Path directory=root.resolve(DIRECTORY);
        if(!Files.isDirectory(directory))return List.of();
        try(var files=Files.list(directory)){
            for(Path file:files.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList())try{
                if(Files.size(file)>262144)throw new IllegalArgumentException("模板大于 256 KiB");
                RecipeTemplate template=new RecipeTemplate(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                for(var e:template.requirements().entrySet()){
                    String error=requirement.apply(e.getKey(),e.getValue().getAsString());if(error!=null&&!error.isBlank())throw new IllegalArgumentException(error);
                }
                SimpleRecipes.yieldToTemplate(template.type());
                if(RecipeAdapters.get(template.type())!=null)throw new IllegalArgumentException("类型已有适配器，不允许覆盖："+template.type());
                RecipeAdapters.register(template);loaded.put(template.type(),template);diagnostics.add(file.getFileName()+"：已加载 · "+template.title());
            }catch(Exception e){diagnostics.add(file.getFileName()+"：未加载 · "+e.getMessage());}
        }catch(Exception e){diagnostics.add("模板目录读取失败："+e.getMessage());}
        return List.copyOf(diagnostics);
    }
    public static RecipeTemplate get(String type){return loaded.get(type);}
    public static List<String> diagnostics(){return List.copyOf(diagnostics);}
    private RecipeTemplates(){}
}
