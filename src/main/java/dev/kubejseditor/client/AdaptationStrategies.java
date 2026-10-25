package dev.kubejseditor.client;

import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import com.google.gson.*;
import java.util.*;
import static dev.kubejseditor.recipe.compat.CompatDefinition.*;

/** Compare actual data/slot coverage, not the mod namespace. Existing confirmed mappings stay pinned. */
public final class AdaptationStrategies {
    public record Option(String name,CompatDefinition definition,int score,int matchedSlots,int slots,int writable,int unresolved,int issues){public String description(){return name+" · 匹配 "+matchedSlots+"/"+slots+" 个 JEI 格子 · "+writable+" 个可读取字段 · "+unresolved+" 个待确认字段";}}
    public static List<Option> inspect(JsonObject data,CompatJeiLayouts.View view,Object nativeValue){
        String type=data.get("type").getAsString();var result=new ArrayList<Option>();var failures=new ArrayList<String>();
        try{add(result,"已有完整字段定义",data,view,CompatibilityRecipes.knownDefinition(type,data,nativeValue),40);}catch(RuntimeException e){failures.add(e.toString());}
        try{add(result,"模组提供的字段",data,view,CompatibilityRecipes.schema(type,data),30);}catch(RuntimeException e){failures.add(e.toString());}
        try{var original=RecipeAdapters.creation(type);if(original!=null)add(result,"已注册编辑模板",data,view,original.presentation(data),25);}catch(RuntimeException e){failures.add(e.toString());}
        try{add(result,"通用物品与流体",data,view,new SimpleRecipeAdapter(data).presentation(data),20);}catch(RuntimeException e){failures.add(e.toString());}
        try{add(result,"JEI 槽位与原始字段",data,view,(RecipePresentation)null,10);}catch(RuntimeException e){failures.add(e.toString());}
        if(result.isEmpty()){var definition=new CompatDefinition(type,"raw-patch");definition.autoDiscover=true;result.add(new Option("原始字段补丁",definition,0,0,view.slots().size(),0,0,0));}
        return result.stream().sorted(Comparator.comparingInt(Option::score).reversed().thenComparing(Option::name)).toList();
    }
    private static void add(List<Option> out,String name,JsonObject data,CompatJeiLayouts.View view,CompatDefinition seed,int priority){if(seed!=null)add(out,name,data,view,new RecipePresentation(data,seed),priority);}
    private static void add(List<Option> out,String name,JsonObject data,CompatJeiLayouts.View view,RecipePresentation seed,int priority){
        if(seed!=null)seed.definition.autoDiscover=true;var p=CompatAutoDiscovery.discover(data,view,seed);var clean=new CompatJeiLayouts.View(view.layout(),view.slots(),null);var positions=CompatJeiLayouts.bind("kubejs_editor:strategy_probe",clean,p);
        int total=(int)view.slots().stream().filter(s->s.role()!=mezz.jei.api.recipe.RecipeIngredientRole.RENDER_ONLY).count();int matched=(int)view.slots().stream().filter(s->s.role()!=mezz.jei.api.recipe.RecipeIngredientRole.RENDER_ONLY&&positions.values().stream().anyMatch(r->CompatJeiLayouts.sameRect(r,s.rect()))).count();int unresolved=(int)p.definition.fields.stream().filter(f->!f.readOnlyReason.isBlank()).count(),writable=p.definition.fields.size()-unresolved;int precise=(int)p.definition.fields.stream().filter(f->!f.unit.isBlank()||f.role==Role.RESOURCE||f.role==Role.CENTER||f.role==Role.CATALYST).count();int issues=p.validate().size();int score=score(matched,total,writable,unresolved,precise,issues,priority);out.add(new Option(name,p.definition,score,matched,total,writable,unresolved,issues));
    }
    public static int score(int matched,int total,int writable,int unresolved,int precise,int issues,int priority){return AdaptationQuality.score(matched,total,writable,unresolved,precise,issues,priority);}
    private AdaptationStrategies(){}
}
