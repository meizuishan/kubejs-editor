package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;
import static dev.kubejseditor.model.EditorProject.requireId;

public final class RecipeAdapters {
    private static final Map<String, RecipeAdapter> TYPES = new LinkedHashMap<>();
    private static final Map<String, RecipeAdapter> CREATION = new LinkedHashMap<>();
    static {
        add("minecraft:crafting_shaped", "工作台 · 有序", "{pattern:['   ','   ','   '],key:{},result:{id:'minecraft:iron_ingot',count:1}}");
        add("minecraft:crafting_shapeless", "工作台 · 无序", "{ingredients:[],result:{id:'minecraft:iron_ingot',count:1}}");
        for (String type : List.of("smelting", "blasting", "smoking", "campfire_cooking")) {
            String name = Map.of("smelting","熔炉","blasting","高炉","smoking","烟熏炉","campfire_cooking","营火").get(type);
            add("minecraft:" + type, name, "{ingredient:{item:'minecraft:iron_ore'},result:{id:'minecraft:iron_ingot'},experience:0.1,cookingtime:200}");
        }
        add("minecraft:stonecutting", "切石机", "{ingredient:{item:'minecraft:stone'},result:{id:'minecraft:stone_slab',count:2}}");
        add("minecraft:smithing_transform", "锻造台 · 转换", "{template:{item:'minecraft:netherite_upgrade_smithing_template'},base:{item:'minecraft:diamond_sword'},addition:{item:'minecraft:netherite_ingot'},result:{id:'minecraft:netherite_sword'}}");
        Map<String,String> names = new LinkedHashMap<>();
        names.put("crushing","粉碎轮"); names.put("milling","石磨"); names.put("mixing","搅拌器"); names.put("compacting","压块");
        names.put("pressing","压片"); names.put("cutting","动力锯"); names.put("deploying","机械手"); names.put("filling","注液器");
        names.put("emptying","分液池"); names.put("splashing","水洗"); names.put("haunting","缠魂"); names.put("sandpaper_polishing","砂纸打磨"); names.put("item_application","手动应用");
        names.forEach((type, title) -> {
            JsonObject json = parse("{ingredients:[{item:'minecraft:iron_ingot'}],results:[{id:'minecraft:iron_nugget',count:1}]}");
            if (List.of("deploying","item_application").contains(type)) json.getAsJsonArray("ingredients").add(parse("{item:'minecraft:redstone'}"));
            if (List.of("mixing","compacting").contains(type)) json.addProperty("heat_requirement", "none");
            if (List.of("crushing","milling","cutting","mixing","compacting").contains(type)) json.addProperty("processing_time", 100);
            if (type.equals("filling")) json.getAsJsonArray("ingredients").add(parse("{type:'neoforge:single',fluid:'minecraft:water',amount:250}"));
            if (type.equals("emptying")) json.getAsJsonArray("results").add(parse("{id:'minecraft:water',amount:250}"));
            add("create:" + type, title, json.toString());
        });
        add("create:mechanical_crafting", "动力合成", "{pattern:['   ','   ','   '],key:{},result:{id:'create:cogwheel',count:1},accept_mirrored:true}");
        add("create:sequenced_assembly", "序列组装", "{ingredient:{item:'create:golden_sheet'},transitional_item:{id:'create:incomplete_precision_mechanism'},loops:5,sequence:[{type:'create:deploying',ingredients:[{item:'create:incomplete_precision_mechanism'},{item:'create:cogwheel'}],results:[{id:'create:incomplete_precision_mechanism'}]}],results:[{id:'create:precision_mechanism',chance:120},{id:'create:golden_sheet',chance:8}]}");
        for(var mode:FanProcessing.all())if(!mode.type().startsWith("create:"))add(mode.type(),"鼓风机 · "+mode.name(),"{ingredients:[{item:'minecraft:iron_ingot'}],results:[{id:'minecraft:iron_nugget'}]}");
        VintageProcessing.register();
    }
    private RecipeAdapters() {}
    public static void register(RecipeAdapter adapter) { TYPES.put(adapter.type(), adapter);if(adapter.canCreate())CREATION.put(adapter.type(),adapter); }
    public static void unregister(String type,RecipeAdapter expected){TYPES.remove(type,expected);CREATION.remove(type,expected);}
    public static Collection<RecipeAdapter> all() { return Collections.unmodifiableCollection(TYPES.values()); }
    public static Collection<RecipeAdapter> creation(){return Collections.unmodifiableCollection(CREATION.values());}
    public static RecipeAdapter creation(String type){return CREATION.get(type);}
    public static RecipeAdapter get(String type) { return TYPES.get(type); }
    public static JsonObject parse(String json) { return JsonParser.parseString(json).getAsJsonObject(); }
    private static void add(String type, String name, String defaults) {
        JsonObject data = parse(defaults); data.addProperty("type", type);
        register(new RecipeAdapter() {
            public String type() { return type; }
            public String title() { return name; }
            public JsonObject newRecipe() { return data.deepCopy(); }
            public List<String> validate(JsonObject d) { return validateData(d); }
        });
    }
    public static List<String> validateData(JsonObject data) {
        List<String> errors = new ArrayList<>();
        try {
            requireId(data.get("type").getAsString());
            String type = data.get("type").getAsString();
            if(get(type) instanceof dev.kubejseditor.recipe.compat.CompatibilityAdapter compat)return compat.validate(data);
            if(get(type) instanceof SimpleRecipeAdapter simple)return simple.validate(data);
            errors.addAll(RecipeLimits.forType(type).validate(data));
            errors.addAll(VintageProcessing.validate(data));
            if(get(type) instanceof RecipeTemplate template)errors.addAll(template.validateFields(data));
            if(data.has("sequence"))for(JsonElement step:data.getAsJsonArray("sequence")){
                for(String error:validateData(step.getAsJsonObject()))errors.add("序列步骤："+error);
            }
            validateTree(data, "", type.equals("create:sequenced_assembly"), errors);
            if (data.has("pattern")) {
                JsonArray rows = data.getAsJsonArray("pattern"); int max = type.startsWith("create:") ? 9 : 3;
                if (rows.isEmpty() || rows.size() > max) errors.add("合成网格高度超限");
                int width = rows.isEmpty() ? 0 : rows.get(0).getAsString().length();
                if (width == 0 || width > max) errors.add("合成网格宽度超限");
                boolean any = false;
                for (JsonElement row : rows) {
                    String s = row.getAsString(); if (s.length() != width) errors.add("合成图案行宽必须一致");
                    for (char c : s.toCharArray()) if (c != ' ') { any = true; if (!data.getAsJsonObject("key").has(String.valueOf(c))) errors.add("图案缺少材料映射：" + c); }
                }
                if (!any) errors.add("请摆放合成材料");
            }
            if (data.has("ingredients") && data.getAsJsonArray("ingredients").isEmpty()) errors.add("请设置输入材料");
            if (data.has("results") && data.getAsJsonArray("results").isEmpty()) errors.add("请设置输出");
            if (type.equals("create:sequenced_assembly")) {
                if (!data.has("sequence") || data.getAsJsonArray("sequence").isEmpty()) errors.add("序列至少需要一个步骤");
                if (data.get("loops").getAsInt() < 1) errors.add("循环次数必须大于零");
                double total = 0;
                for (JsonElement out : data.getAsJsonArray("results")) total += out.getAsJsonObject().has("chance") ? out.getAsJsonObject().get("chance").getAsDouble() : 1;
                if (!(total > 0)) errors.add("序列产物权重之和必须大于零");
            }
        } catch (Exception e) { errors.add("配方结构无效：" + e.getMessage()); }
        return errors;
    }
    private static void validateTree(JsonElement node, String path, boolean weighted, List<String> errors) {
        if(node.isJsonNull()&&path.matches(".*/(ingredients|results)/[0-9]+"))errors.add(path+" 请填入材料或产物");
        if (node.isJsonArray()) { int n = 0; for (JsonElement e : node.getAsJsonArray()) validateTree(e, path + "/" + n++, weighted, errors); }
        if (!node.isJsonObject()) return;
        for (var e : node.getAsJsonObject().entrySet()) {
            String key = e.getKey(); JsonElement v = e.getValue(); String at = path + "/" + key;
            if(v.isJsonNull()&&Set.of("ingredient","result","template","base","addition","transitional_item").contains(key))errors.add(at+" 不能为空");
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
                double x = v.getAsDouble();
                if (!Double.isFinite(x)) errors.add(at + " 必须是有限数值");
                if (Set.of("count","amount","cookingtime","loops").contains(key) && (x < 1 || x != Math.rint(x))) errors.add(at + " 必须是正整数");
                if (Set.of("processing_time","experience","chance").contains(key) && x < 0) errors.add(at + " 不能为负数");
                if (key.equals("chance") && !(weighted && path.startsWith("/results/")) && x > 1) errors.add(at + " 概率应在 0～1 之间");
            }
            validateTree(v, at, weighted, errors);
        }
    }
}
