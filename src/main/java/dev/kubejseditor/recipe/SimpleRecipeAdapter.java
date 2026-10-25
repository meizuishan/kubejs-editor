package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;
import dev.kubejseditor.recipe.compat.*;
import static dev.kubejseditor.recipe.compat.CompatDefinition.*;
import static dev.kubejseditor.model.EditorProject.requireId;

/** Experimental, document-preserving fields. No machine semantics are inferred from display text. */
public final class SimpleRecipeAdapter implements RecipeAdapter {
    private final String type;
    private final JsonObject example;
    private static final Set<String> INPUTS=Set.of("ingredient","ingredients","input","inputs","itemInput","itemInputs","fluidInput","fluidInputs","inputFluid","inputFluids","fluid_input","fluid_inputs");
    private static final Set<String> OUTPUTS=Set.of("result","results","output","outputs","itemOutput","itemOutputs","fluidOutput","fluidOutputs","outputFluid","outputFluids","fluid_output","fluid_outputs");
    public record Binding(List<String> path,boolean output,Format format){public String pointer(){return "/"+String.join("/",path);}}
    public SimpleRecipeAdapter(JsonObject source){type=source.get("type").getAsString();requireId(type);example=source.deepCopy();bindings(source);}
    public String type(){return type;}
    @Override public boolean canCreate(){return false;}
    public String title(){return type+" · 简单适配（实验）";}
    public JsonObject newRecipe(){return example.deepCopy();}
    /** Reuses the original codecs instead of converting native wrappers into a guessed format. */
    @Override public RecipePresentation presentation(JsonObject data){
        var definition=new CompatDefinition(type,"legacy-simple");var formats=new LinkedHashMap<String,Binding>();
        for(var b:bindings(data)){var f=definition.add(b.pointer(),b.output?"产物":"输入",b.format.fluid?Kind.FLUID:Kind.ITEM,b.output?Role.OUTPUT:Role.INPUT);f.required=true;var v=b.format.read(get(data,b.path));if(!b.format.text&&(b.format.fluid||v.has("count")))f.quantity=b.format.fluid?"amount":"count";if(v.has("chance"))f.probability="chance";formats.put(b.pointer(),b);}
        for(String path:parameters(data)){var v=get(data,List.of(path.split("/")));definition.add("/"+path,path,v.getAsJsonPrimitive().isBoolean()?Kind.BOOLEAN:Kind.NUMBER,Role.PARAMETER);}
        return new RecipePresentation(data,definition){
            @Override public JsonObject item(RecipePresentation.Binding binding){var b=formats.get(binding.path());var v=b.format.read(get(data,b.path));if(!v.has(binding.fluid()?"amount":"count"))v.addProperty(binding.fluid()?"amount":"count",1);return v;}
            @Override public void writeItem(RecipePresentation.Binding binding,JsonObject edit){var b=formats.get(binding.path());var next=edit.deepCopy();if(binding.field().quantity.isBlank()&&next.has("count")){if(next.get("count").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE)!=0)throw new IllegalArgumentException("此原料数量固定为 1");next.remove("count");}set(data,b.path,b.format.write(get(data,b.path),next));}
            @Override public void remove(RecipePresentation.Binding binding){throw new IllegalArgumentException("简单适配保留原有槽位，请替换材料或产物");}
            @Override public List<String> validate(){return SimpleRecipeAdapter.this.validate(data);}
            @Override public void verifyRoundTrip(JsonObject encoded){SimpleRecipeAdapter.this.verifyRoundTrip(data,encoded);}
        };
    }
    @Override public RecipeLimits limits(){return new RecipeLimits(32,32,32,32);}
    @Override public List<String> validate(JsonObject data){try{
        for(var b:bindings(data)){var value=b.format.read(get(data,b.path));for(String key:List.of("count","amount"))if(value.has(key)){double n=value.get(key).getAsDouble();if(!Double.isFinite(n)||n<1||n!=Math.rint(n))throw new IllegalArgumentException(b.pointer()+" / "+key+" 必须为正整数");}if(value.has("chance")){double chance=value.get("chance").getAsDouble();if(!Double.isFinite(chance)||chance<0||chance>1)throw new IllegalArgumentException("非 0～1 概率需要专用适配："+b.pointer());}}
        for(String key:parameters(data)){var value=get(data,List.of(key.split("/")));if(value.getAsJsonPrimitive().isNumber()&&!Double.isFinite(value.getAsDouble()))throw new IllegalArgumentException("参数必须为有限数：/"+key);}return List.of();
    }catch(RuntimeException e){return List.of(e.getMessage());}}
    public static List<Binding> bindings(JsonObject data){
        if(data.has("pattern")||data.has("sequence")||data.has("key"))throw new IllegalArgumentException("网格、序列或动态配方需要专用适配器");
        var out=new ArrayList<Binding>();
        for(var e:data.entrySet()){
            boolean input=INPUTS.contains(e.getKey()),output=OUTPUTS.contains(e.getKey());if(!input&&!output)continue;
            boolean fluid=e.getKey().toLowerCase(Locale.ROOT).contains("fluid");
            if(e.getValue().isJsonArray()){
                if(Set.of("ingredient","input","itemInput","fluidInput","inputFluid","fluid_input").contains(e.getKey()))throw new IllegalArgumentException("单个输入字段的数组可能是 OR 原料，需要专用适配");
                var array=e.getValue().getAsJsonArray();if(array.isEmpty())throw new IllegalArgumentException("简单适配不增加或删除槽位：/"+e.getKey());
                for(int i=0;i<array.size();i++){if(output&&inactiveOutput(array.get(i)))continue;out.add(new Binding(List.of(e.getKey(),String.valueOf(i)),output,Format.inspect(array.get(i),output,fluid)));}
            }else out.add(new Binding(List.of(e.getKey()),output,Format.inspect(e.getValue(),output,fluid)));
        }
        if(out.size()>32||out.stream().noneMatch(Binding::output)||out.stream().allMatch(Binding::output))throw new IllegalArgumentException("仅支持有明确输入、产物且总槽位不超过 32 的简单配方");
        return List.copyOf(out);
    }
    /** Some serializers encode a disabled optional result as an empty stack with zero chance. */
    private static boolean inactiveOutput(JsonElement value){
        if(!value.isJsonObject())return false;var object=value.getAsJsonObject();
        if(!object.has("chance")||!object.get("chance").isJsonPrimitive()||!object.get("chance").getAsJsonPrimitive().isNumber()||object.get("chance").getAsBigDecimal().signum()!=0)return false;
        return object.has("result")&&object.get("result").isJsonObject()&&object.getAsJsonObject("result").isEmpty();
    }
    public List<RecipeCanvas.Slot> slots(JsonObject data){
        var result=new ArrayList<RecipeCanvas.Slot>();int in=0,out=0;
        for(var binding:bindings(data)){int n=binding.output?out++:in++;
            result.add(new RecipeCanvas.Slot((binding.output?164:0)+n%3*28,n/3*38,(binding.output?"产物 ":"输入 ")+binding.pointer(),binding.output,binding.format.fluid,()->binding.format.read(get(data,binding.path)),value->{
                if(value.isJsonNull())throw new IllegalArgumentException("简单适配保留原有槽位，请替换物品而非清空");
                JsonElement before=get(data,binding.path);set(data,binding.path,binding.format.write(before,value));
            }));
        }return result;
    }
    public void verifyRoundTrip(JsonObject proposed,JsonObject encoded){
        for(var b:bindings(proposed)){
            JsonElement actual=get(encoded,b.path);if(actual==null||actual.isJsonNull())throw new IllegalArgumentException("原生 Codec 丢弃了编辑字段："+b.pointer());
            JsonObject wanted=b.format.read(get(proposed,b.path));JsonObject found=Format.inspect(actual,b.output,b.format.fluid).read(actual);
            for(String key:List.of("id","item","tag","items","count","amount","components","chance")){
                JsonElement left=wanted.get(key),right=found.get(key);
                if(key.equals("chance")){if(left==null)left=new JsonPrimitive(1);if(right==null)right=new JsonPrimitive(1);}
                if(!jsonEqual(left,right))throw new IllegalArgumentException("原生 Codec 未保留槽位修改："+b.pointer()+" / "+key);
            }
        }
        for(String path:parameters(proposed)){JsonElement value=get(proposed,List.of(path.split("/"))),actual=get(encoded,List.of(path.split("/")));if(!jsonEqual(value,actual))throw new IllegalArgumentException("原生 Codec 未保留参数修改：/"+path);}
    }
    private static boolean jsonEqual(JsonElement a,JsonElement b){
        if(a==null||b==null)return a==b;if(a.isJsonPrimitive()&&b.isJsonPrimitive()&&a.getAsJsonPrimitive().isNumber()&&b.getAsJsonPrimitive().isNumber())return a.getAsBigDecimal().compareTo(b.getAsBigDecimal())==0;
        if(a.isJsonArray()&&b.isJsonArray()){if(a.getAsJsonArray().size()!=b.getAsJsonArray().size())return false;for(int i=0;i<a.getAsJsonArray().size();i++)if(!jsonEqual(a.getAsJsonArray().get(i),b.getAsJsonArray().get(i)))return false;return true;}
        if(a.isJsonObject()&&b.isJsonObject()){if(!a.getAsJsonObject().keySet().equals(b.getAsJsonObject().keySet()))return false;for(String key:a.getAsJsonObject().keySet())if(!jsonEqual(a.getAsJsonObject().get(key),b.getAsJsonObject().get(key)))return false;return true;}return a.equals(b);
    }
    public static List<String> parameters(JsonObject data){var result=new ArrayList<String>();collectParameters(data,"",result,0);return List.copyOf(result);}
    private static void collectParameters(JsonObject data,String prefix,List<String> result,int depth){
        for(var entry:data.entrySet()){
            if(INPUTS.contains(entry.getKey())||OUTPUTS.contains(entry.getKey())||Set.of("type","components","neoforge:conditions").contains(entry.getKey()))continue;
            String path=prefix+entry.getKey();JsonElement value=entry.getValue();
            if(value.isJsonPrimitive()&&(value.getAsJsonPrimitive().isNumber()||value.getAsJsonPrimitive().isBoolean()))result.add(path);
            else if(depth<2&&value.isJsonObject())collectParameters(value.getAsJsonObject(),path+"/",result,depth+1);
        }
    }
    public static JsonElement get(JsonElement data,List<String> path){
        JsonElement current=data;for(String part:path){if(current==null||current.isJsonNull())return null;if(current.isJsonArray()){int i=Integer.parseInt(part);if(i>=current.getAsJsonArray().size())return null;current=current.getAsJsonArray().get(i);}else if(current.isJsonObject())current=current.getAsJsonObject().get(part);else return null;}return current;
    }
    public static void set(JsonObject data,List<String> path,JsonElement value){JsonElement parent=get(data,path.subList(0,path.size()-1));String last=path.getLast();if(parent.isJsonArray())parent.getAsJsonArray().set(Integer.parseInt(last),value);else parent.getAsJsonObject().add(last,value);}

    public static final class Format {
        private final String key,wrapper,quantity;private final Format child;private final boolean output,fluid,text;
        private Format(String key,String wrapper,String quantity,Format child,boolean output,boolean fluid,boolean text){this.key=key;this.wrapper=wrapper;this.quantity=quantity;this.child=child;this.output=output;this.fluid=fluid;this.text=text;}
        public static Format inspect(JsonElement value,boolean output,boolean fluidHint){
            if(value==null||value.isJsonNull()||value.isJsonArray())throw new IllegalArgumentException("包含空槽、OR 原料或自定义数组，简单适配保持只读");
            if(value.isJsonPrimitive()){
                if(!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("槽位不是物品或流体 ID");String id=value.getAsString();requireId(id.startsWith("#")?id.substring(1):id);
                if(fluidHint||output&&id.startsWith("#"))throw new IllegalArgumentException("流体字符串或标签产物需要专用数量映射");
                return new Format("",null,null,null,output,fluidHint,true);
            }
            var o=value.getAsJsonObject();
            if(o.has("type")&&!Set.of("neoforge:single","neoforge:tag","neoforge:components").contains(o.get("type").getAsString()))throw new IllegalArgumentException("自定义原料类型需专用适配："+o.get("type").getAsString());
            for(String wrapper:List.of("ingredient","result","item","fluid"))if(o.has(wrapper)&&o.get(wrapper).isJsonObject()){
                boolean fluid=fluidHint||wrapper.equals("fluid");var nested=inspect(o.get(wrapper),output,fluid);
                String quantity=!output&&wrapper.equals("ingredient")?(o.has("amount")?"amount":o.has("count")?"count":null):null;
                return new Format("",wrapper,quantity,nested,output,nested.fluid,false);
            }
            var identities=new ArrayList<String>();for(String key:List.of("item","id","tag","items","fluid"))if(o.has(key)&&o.get(key).isJsonPrimitive()&&o.get(key).getAsJsonPrimitive().isString())identities.add(key);
            if(identities.size()!=1)throw new IllegalArgumentException("槽位缺少唯一物品、标签或流体字段");String key=identities.getFirst(),id=o.get(key).getAsString();requireId(id.startsWith("#")?id.substring(1):id);
            if(output&&(key.equals("tag")||id.startsWith("#")))throw new IllegalArgumentException("标签产物需要专用适配");
            boolean fluid=fluidHint||key.equals("fluid")||o.has("amount")&&key.equals("id")||o.has("type")&&Set.of("neoforge:single","neoforge:tag").contains(o.get("type").getAsString());
            return new Format(key,null,null,null,output,fluid,false);
        }
        public JsonObject read(JsonElement value){
            if(wrapper!=null){var o=value.getAsJsonObject();var result=child.read(o.get(wrapper));if(quantity!=null)result.add("count",o.get(quantity).deepCopy());if(output&&o.has("chance"))result.add("chance",o.get("chance").deepCopy());return result;}
            JsonObject result=text?new JsonObject():value.getAsJsonObject().deepCopy();String id=text?value.getAsString():result.remove(key).getAsString();
            if(key.equals("items")){result.addProperty("items",id);return result;}
            String identity=id.startsWith("#")||key.equals("tag")?"tag":output?"id":fluid?"fluid":"item";result.addProperty(identity,id.startsWith("#")?id.substring(1):id);
            if(fluid){if(!result.has("amount"))result.addProperty("amount",1000);if(!output&&!result.has("type"))result.addProperty("type",identity.equals("tag")?"neoforge:tag":"neoforge:single");}
            else if(output&&!result.has("count"))result.addProperty("count",1);
            return result;
        }
        public JsonElement write(JsonElement before,JsonElement canonical){
            if(!canonical.isJsonObject())throw new IllegalArgumentException("请使用物品或流体槽位输入");var edit=canonical.getAsJsonObject();
            if(wrapper!=null){var original=before.getAsJsonObject().deepCopy();var body=edit.deepCopy();if(quantity!=null){if(body.has("count"))original.add(quantity,body.remove("count"));}
                if(output&&original.has("chance")&&body.has("chance"))original.add("chance",body.remove("chance"));
                original.add(wrapper,child.write(original.get(wrapper),body));return original;
            }
            String field=edit.has("tag")?"tag":edit.has("items")?"items":edit.has("id")?"id":edit.has("fluid")?"fluid":"item";
            if(!edit.has(field)||!edit.get(field).isJsonPrimitive())throw new IllegalArgumentException("请选择明确的物品、流体或标签");String id=edit.get(field).getAsString();requireId(id.startsWith("#")?id.substring(1):id);
            if(text){if(edit.has("components")||edit.has("count")&&edit.get("count").getAsInt()!=1||edit.has("amount"))throw new IllegalArgumentException("字符串槽位不支持数量或组件扩展");return new JsonPrimitive(field.equals("tag")?"#"+id:id);}
            var result=before.getAsJsonObject().deepCopy();for(String identity:List.of("id","item","tag","items","fluid"))result.remove(identity);
            if(!output&&edit.has("type")&&edit.get("type").getAsString().equals("neoforge:components")){
                result.addProperty("type","neoforge:components");result.addProperty("items",field.equals("tag")?"#"+id:id);if(edit.has("strict"))result.add("strict",edit.get("strict"));
            }else{
                String target=field.equals("tag")?"tag":key.equals("tag")?(output?"id":fluid?"fluid":"item"):key;
                if(!output&&!fluid&&result.has("type")&&result.get("type").getAsString().equals("neoforge:components")){result.remove("type");result.remove("strict");target=field.equals("tag")?"tag":"item";}
                result.addProperty(target,id);
                if(!output&&fluid&&result.has("type"))result.addProperty("type",target.equals("tag")?"neoforge:tag":"neoforge:single");
            }
            for(String editable:List.of("count","amount","components")){if(edit.has(editable))result.add(editable,edit.get(editable).deepCopy());else if(editable.equals("components"))result.remove(editable);}
            if(result.has("chance")&&edit.has("chance"))result.add("chance",edit.get("chance").deepCopy());
            return result;
        }
    }
}
