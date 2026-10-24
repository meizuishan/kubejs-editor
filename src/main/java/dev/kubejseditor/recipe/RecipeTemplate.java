package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;
import static dev.kubejseditor.model.EditorProject.requireId;

/** Data-only v1 extension: standard ingredients/results, with lossless JSON slot edits. */
public final class RecipeTemplate implements RecipeAdapter {
    private final JsonObject definition, defaults;
    private final RecipeLimits limits;
    public RecipeTemplate(JsonObject source) {
        definition=source.deepCopy();
        if(source.get("schemaVersion").getAsInt()!=1)throw new IllegalArgumentException("模板版本必须为 1");
        requireId(type());
        if(title().isBlank()||title().length()>120)throw new IllegalArgumentException("模板名称不能为空或过长");
        JsonObject l=source.getAsJsonObject("limits");
        limits=new RecipeLimits(limit(l,"inputs"),limit(l,"outputs"),limit(l,"fluidInputs"),limit(l,"fluidOutputs"));
        if(limits.inputs()+limits.fluidInputs()==0||limits.outputs()+limits.fluidOutputs()==0)throw new IllegalArgumentException("模板至少需要输入和产物");
        defaults=source.getAsJsonObject("defaults").deepCopy();defaults.addProperty("type",type());
        defaults.getAsJsonArray("ingredients").size();defaults.getAsJsonArray("results").size();
        if(defaults.has("pattern")||defaults.has("sequence"))throw new IllegalArgumentException("模板 v1 仅支持 ingredients/results 加工结构");
        Set<String> keys=new HashSet<>();
        for(JsonElement e:parameters()){
            JsonObject p=e.getAsJsonObject();String key=p.get("key").getAsString();
            if(!key.matches("[a-z][a-z0-9_]*")||Set.of("type","ingredients","results").contains(key)||!keys.add(key))throw new IllegalArgumentException("重复或无效的参数："+key);
            if(!Set.of("integer","number","boolean","string").contains(p.get("kind").getAsString()))throw new IllegalArgumentException("不支持的参数类型："+key);
            if(!defaults.has(key)&&p.has("default"))defaults.add(key,p.get("default").deepCopy());
            if(p.has("min")&&p.has("max")&&p.get("min").getAsDouble()>p.get("max").getAsDouble())throw new IllegalArgumentException("参数范围颠倒："+key);
        }
        List<String> errors=validateFields(defaults);errors.addAll(limits.validate(defaults));
        if(!errors.isEmpty())throw new IllegalArgumentException(String.join("；",errors));
    }
    private static int limit(JsonObject l,String key){int n=l.has(key)?l.get(key).getAsInt():0;if(n<0||n>64)throw new IllegalArgumentException("槽位上限须在 0～64："+key);return n;}
    public String type(){return definition.get("type").getAsString();}
    public String title(){return definition.get("title").getAsString();}
    public RecipeLimits limits(){return limits;}
    public JsonObject newRecipe(){return defaults.deepCopy();}
    public JsonArray parameters(){return definition.has("parameters")?definition.getAsJsonArray("parameters"):new JsonArray();}
    public JsonObject requirements(){return definition.has("requires")?definition.getAsJsonObject("requires"):new JsonObject();}
    public List<String> validate(JsonObject data){return RecipeAdapters.validateData(data);}
    public List<String> validateFields(JsonObject data){
        List<String> errors=new ArrayList<>();
        for(JsonElement entry:parameters()){
            JsonObject p=entry.getAsJsonObject();String key=p.get("key").getAsString(),label=p.has("label")?p.get("label").getAsString():key;
            try{
                JsonElement e=data.get(key);if(e==null||!e.isJsonPrimitive())throw new IllegalArgumentException("缺少参数");
                JsonPrimitive v=e.getAsJsonPrimitive();String kind=p.get("kind").getAsString();
                if(kind.equals("boolean")&&!v.isBoolean()||kind.equals("string")&&!v.isString())throw new IllegalArgumentException("参数类型错误");
                if(Set.of("integer","number").contains(kind)){
                    if(!v.isNumber())throw new IllegalArgumentException("需要数值");double n=v.getAsDouble();
                    if(!Double.isFinite(n)||kind.equals("integer")&&n!=Math.rint(n))throw new IllegalArgumentException("需要有效"+(kind.equals("integer")?"整数":"数值"));
                    if(p.has("min")&&n<p.get("min").getAsDouble()||p.has("max")&&n>p.get("max").getAsDouble())throw new IllegalArgumentException("超出范围 "+range(p));
                }
                if(p.has("values")&&!p.getAsJsonArray("values").contains(v))throw new IllegalArgumentException("需要从允许值中选择");
            }catch(Exception ex){errors.add("/"+key+" "+label+"："+ex.getMessage());}
        }return errors;
    }
    public static String range(JsonObject p){return (p.has("min")?p.get("min").getAsString():"−∞")+"～"+(p.has("max")?p.get("max").getAsString():"∞");}
    public String slotLabel(boolean output){String key=output?"outputLabel":"inputLabel";return definition.has(key)?definition.get(key).getAsString():output?"产物":"输入";}
    public List<RecipeCanvas.Slot> slots(JsonObject data){
        List<RecipeCanvas.Slot> slots=new ArrayList<>();
        for(boolean output:new boolean[]{false,true}){
            JsonArray array=data.getAsJsonArray(output?"results":"ingredients");
            for(int i=0;i<array.size();i++){int index=i;
                slots.add(new RecipeCanvas.Slot((output?164:0)+i%3*28,i/3*38,slotLabel(output)+" "+(i+1),output,RecipeLimits.fluid(array.get(i)),()->index<array.size()?array.get(index):JsonNull.INSTANCE,v->{if(v.isJsonNull())array.remove(index);else array.set(index,v);}));
            }
        }return slots;
    }
}
