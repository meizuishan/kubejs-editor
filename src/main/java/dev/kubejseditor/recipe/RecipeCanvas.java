package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Slot positions are view data; every mutation goes into the JSON document. */
public final class RecipeCanvas {
    public static List<Slot> slots(dev.kubejseditor.model.EditorProject.RecipeDraft draft){if(draft.operation.equals("remove"))return List.of();return CraftingGrid.supports(draft.data)?CraftingGrid.slots(draft):slots(draft.data);}
    public record Slot(int x, int y, String label, boolean output, boolean fluid, java.util.function.Supplier<JsonElement> read, java.util.function.Consumer<JsonElement> write) {
        public JsonElement get() { return read.get(); }
        public void set(JsonElement data) { write.accept(data.deepCopy()); }
    }
    public static List<Slot> slots(JsonObject recipe) {
        if(!recipe.has("type"))return List.of();
        String serializer=recipe.get("type").getAsString();
        if(RecipeAdapters.get(serializer) instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter compatibility){var view=compatibility.presentation(recipe);var list=new ArrayList<Slot>();int n=0;for(var b:view.bindings())if(b.field().slot()&&b.field().kind!=dev.kubejseditor.recipe.compat.CompatDefinition.Kind.OTHER&&!view.nativeValue(b).isJsonNull()){int i=n++;list.add(new Slot((i%8)*24,(i/8)*28,b.field().label,b.output(),b.fluid(),()->view.item(b),v->{if(v.isJsonNull())view.remove(b);else view.writeItem(b,v.getAsJsonObject());}));}return list;}
        if(RecipeAdapters.get(serializer) instanceof SimpleRecipeAdapter simple)return simple.slots(recipe);
        if(RecipeAdapters.get(serializer) instanceof RecipeTemplate template)return template.slots(recipe);
        if(recipe.has("sequence"))return SequenceLayout.slots(recipe);
        if(VintageProcessing.supports(serializer))return VintageProcessing.slots(recipe);
        if(FanProcessing.createType(serializer)&&!recipe.has("sequence")&&recipe.has("ingredients")&&recipe.has("results"))return CreateProcessingLayout.slots(recipe);
        List<Slot> out = new ArrayList<>(); String type = recipe.get("type").getAsString();
        if (recipe.has("pattern")) {
            JsonArray pattern=recipe.getAsJsonArray("pattern"); JsonObject key=recipe.getAsJsonObject("key");
            for(int y=0;y<pattern.size();y++) for(int x=0;x<pattern.get(y).getAsString().length();x++) {
                int row=y,col=x; String symbol = String.valueOf(pattern.get(y).getAsString().charAt(x));
                out.add(new Slot(x*24,y*24,"材料 "+(y+1)+","+(x+1),false,false,
                    () -> { String s=String.valueOf(pattern.get(row).getAsString().charAt(col)); return key.has(s)?key.get(s):JsonNull.INSTANCE; },
                    value -> {
                        char[] line=pattern.get(row).getAsString().toCharArray(); String k=String.valueOf((char)('A'+row*9+col));
                        if(value.isJsonNull()){ line[col]=' '; } else { line[col]=k.charAt(0); key.add(k,value); }
                        pattern.set(row,new JsonPrimitive(new String(line)));
                        Set<String> used=new HashSet<>();for(JsonElement r:pattern)for(char c:r.getAsString().toCharArray())used.add(String.valueOf(c));
                        key.keySet().removeIf(s -> !used.contains(s));
                    }));
            }
        } else if(type.contains("smithing")) {
            property(out,recipe,"template",0,0,"模板",false,false);
            property(out,recipe,"base",28,0,"基底",false,false);
            property(out,recipe,"addition",56,0,"添加物",false,false);
        } else if(recipe.has("ingredients")) array(out,recipe.getAsJsonArray("ingredients"),0,0,"输入",false);
        else if(recipe.has("ingredient")) property(out,recipe,"ingredient",0,recipe.has("sequence")?135:0,"输入",false,false);
        int outputY=recipe.has("sequence")?135:0;
        if(recipe.has("result")) property(out,recipe,"result",recipe.has("pattern")?recipe.getAsJsonArray("pattern").get(0).getAsString().length()*24+48:120,recipe.has("pattern")?recipe.getAsJsonArray("pattern").size()/2*24:0,"输出",true,false);
        if(recipe.has("results")) array(out,recipe.getAsJsonArray("results"),recipe.has("sequence")?132:164,outputY,"产物",true);
        if(recipe.has("transitional_item")) property(out,recipe,"transitional_item",64,135,"中间件",true,false);
        if(recipe.has("sequence")) {
            JsonArray steps=recipe.getAsJsonArray("sequence");
            for(int i=0;i<steps.size();i++) {
                JsonObject step=steps.get(i).getAsJsonObject(); JsonArray inputs=step.getAsJsonArray("ingredients");
                if(inputs.size()>1){int index=1;JsonElement candidate=inputs.get(1);boolean fluid=step.get("type").getAsString().equals("create:filling")||candidate.isJsonObject()&&(candidate.getAsJsonObject().has("fluid")||candidate.getAsJsonObject().has("amount"));
                    out.add(new Slot(i*64+16,72,"步骤 "+(i+1),false,fluid,()->inputs.get(index),v->inputs.set(index,v)));}
            }
        }
        return out;
    }
    private static void property(List<Slot> slots,JsonObject owner,String key,int x,int y,String label,boolean output,boolean fluid) {
        slots.add(new Slot(x,y,label,output,fluid,()->owner.has(key)?owner.get(key):JsonNull.INSTANCE,v->owner.add(key,v)));
    }
    private static void array(List<Slot> slots,JsonArray owner,int x,int y,String label,boolean output) {
        for(int i=0;i<owner.size();i++){int index=i;JsonElement e=owner.get(i);boolean fluid=e.isJsonObject()&&(e.getAsJsonObject().has("fluid")||e.getAsJsonObject().has("amount"));
            slots.add(new Slot(x+(i%4)*28,y+(i/4)*36,label+" "+(i+1),output,fluid,()->index<owner.size()?owner.get(index):JsonNull.INSTANCE,v->{if(v.isJsonNull())owner.remove(index);else owner.set(index,v);}));}
    }
    public static void addStep(JsonObject recipe,String type) {
        String intermediate=recipe.getAsJsonObject("transitional_item").get("id").getAsString();
        JsonObject step=RecipeAdapters.parse("{type:'create:pressing',ingredients:[],results:[]}");step.addProperty("type",type);
        if(VintageProcessing.assemblySupported(type)){step=VintageProcessing.defaults(type);step.add("ingredients",new JsonArray());step.add("results",new JsonArray());}
        JsonObject input=new JsonObject();input.addProperty("item",intermediate);step.getAsJsonArray("ingredients").add(input);
        if(type.equals("create:deploying"))step.getAsJsonArray("ingredients").add(JsonNull.INSTANCE);
        if(type.equals("create:filling"))step.getAsJsonArray("ingredients").add(RecipeAdapters.parse("{amount:250}"));
        JsonObject output=new JsonObject();output.addProperty("id",intermediate);step.getAsJsonArray("results").add(output);
        recipe.getAsJsonArray("sequence").add(step);
    }
    public static void normalizeTransition(JsonObject recipe) {
        if(!recipe.has("sequence")||!recipe.has("transitional_item")||!recipe.get("transitional_item").isJsonObject()||!recipe.getAsJsonObject("transitional_item").has("id"))return;String id=recipe.getAsJsonObject("transitional_item").get("id").getAsString();
        for(JsonElement e:recipe.getAsJsonArray("sequence")) {JsonObject step=e.getAsJsonObject();JsonObject input=new JsonObject();input.addProperty("item",id);step.getAsJsonArray("ingredients").set(0,input);JsonObject output=new JsonObject();output.addProperty("id",id);step.getAsJsonArray("results").set(0,output);}
    }
    public static JsonObject withComponents(JsonObject source,JsonObject components,boolean output){
        JsonObject value=source.deepCopy();
        if(output){if(components.isEmpty())value.remove("components");else value.add("components",components);return value;}
        if(components.isEmpty()){
            value.remove("components");
            if(value.has("type")&&value.get("type").getAsString().equals("neoforge:components")&&value.has("items")&&value.get("items").isJsonPrimitive()){
                String id=value.remove("items").getAsString();value.remove("type");value.remove("strict");value.addProperty(id.startsWith("#")?"tag":"item",id.startsWith("#")?id.substring(1):id);
            }return value;
        }
        String ids=value.has("items")?value.get("items").getAsString():value.has("tag")?"#"+value.get("tag").getAsString():value.has("item")?value.get("item").getAsString():null;
        if(ids==null)throw new IllegalArgumentException("请先设置物品或标签，再设置原料组件");
        value.remove("item");value.remove("tag");value.addProperty("type","neoforge:components");value.addProperty("items",ids);value.add("components",components);if(!value.has("strict"))value.addProperty("strict",false);return value;
    }
}
