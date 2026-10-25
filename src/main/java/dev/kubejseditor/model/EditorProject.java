package dev.kubejseditor.model;

import com.google.gson.*;
import java.util.*;

/** Authoring state, independent of loaded registries and generated JavaScript. */
public final class EditorProject {
    public int schemaVersion = 2;
    public Map<String, RecipeDraft> recipes = new LinkedHashMap<>();
    public Map<String, ItemDraft> items = new LinkedHashMap<>();
    public List<ConfigDraft> configs = new ArrayList<>();
    public Map<String,String> assets = new LinkedHashMap<>();
    public Map<String,ScriptDraft> scripts = new LinkedHashMap<>();
    public static final class ScriptDraft {
        public String path,baseHash,text;
        public boolean takeover;
    }
    public static final class ConfigDraft {
        public String modId,file,type,value,target;
        public List<String> path=new ArrayList<>();
    }

    public static final class RecipeDraft {
        public String id;
        public String operation = "add";
        /** Preserve whether this ID was created here when later edits become replacements. */
        public String origin;
        public GridDraft grid;
        /** Optional schema-2 authoring metadata, never emitted into the native recipe JSON. */
        public boolean simple;
        public JsonObject compatibility;
        /** Optional schema-2 policy. Null preserves legacy scripts; force workbench defaults to ignore. */
        public String loadConditionPolicy;
        /** Force workbench delegates validity to the registered native serializer. */
        public boolean forceNative;
        /** Native datapack entry destination; absent for ordinary RecipeManager recipes. */
        public RegistryEntryTarget registryTarget;
        public boolean enabled = true;
        public JsonObject data;
        /**
         * Optional snapshot of the source recipe before this rule first replaced or disabled it.
         * Never emitted into generated scripts; schema 2 readers without this field keep working.
         */
        public JsonObject source;
        public RecipeDraft() {}
        public RecipeDraft(String id, JsonObject data) { this.id = id; this.data = data.deepCopy(); }
        public String actionLabel(){return !enabled?"规则未启用":operation.equals("remove")?"已禁用":operation.equals("add")||"add".equals(origin)?"新增":"已替换";}
        public RecipeDraft copy(String newId) {
            RecipeDraft d = new RecipeDraft(newId, data == null ? new JsonObject() : data);
            d.enabled = enabled;d.origin=origin;d.simple=simple;d.grid=grid==null?null:grid.copy();d.loadConditionPolicy=loadConditionPolicy;d.forceNative=forceNative;
            d.source=source==null?null:source.deepCopy();d.compatibility=compatibility==null?null:compatibility.deepCopy();d.registryTarget=registryTarget==null?null:dev.kubejseditor.storage.WorkspaceState.copy(registryTarget,RegistryEntryTarget.class);if(d.registryTarget!=null){d.registryTarget.entry=newId;d.id=d.registryTarget.id();}return d;
        }
    }
    /** Editor placement stays separate from the trimmed recipe consumed by Minecraft. */
    public static final class GridDraft {
        public int size=3;
        public JsonArray cells=new JsonArray();
        public GridDraft copy(){GridDraft g=new GridDraft();g.size=size;g.cells=cells.deepCopy();return g;}
    }

    public static final class ItemDraft {
        public String id = "kubejs:new_item";
        public boolean register = true;
        public boolean food = false;
        public String kind = "basic";
        public JsonObject settings = new JsonObject();
        public BlockOptions block = new BlockOptions();
        public String name = "新物品";
        public String nameKey = "";
        public String texture = "minecraft:item/iron_ingot";
        /** Only explicitly chosen properties are emitted when modifying existing items. */
        public JsonObject properties = new JsonObject();
        public List<String> tooltip = new ArrayList<>();
        public List<AttributeDraft> attributes = new ArrayList<>();
        public boolean replaceAttributes = false;
    }

    public static final class AttributeDraft {
        public String attribute = "minecraft:generic.attack_damage";
        public String id = "kubejs_editor:attack_damage";
        public double amount = 1;
        public String operation = "add_value";
        public String slot = "mainhand";
    }

    public void validateStructure() {
        if (schemaVersion != 2) throw new IllegalArgumentException("不支持的草稿版本：" + schemaVersion);
        if (recipes == null || items == null) throw new IllegalArgumentException("草稿缺少 recipes/items");
        if(configs==null||assets==null||scripts==null)throw new IllegalArgumentException("草稿中的 configs/assets/scripts 不能为 null");
        recipes.forEach((id, d) -> {
            requireId(id); if (d == null || !id.equals(d.id)) throw new IllegalArgumentException("草稿配方 ID 不一致：" + id);
            if(d.registryTarget!=null){d.registryTarget.check();if(!id.equals(d.registryTarget.id()))throw new IllegalArgumentException("注册表来源与编辑 ID 不一致："+id);}
            if (!Set.of("add", "replace", "remove").contains(d.operation)) throw new IllegalArgumentException("未知操作：" + d.operation);
            if(d.loadConditionPolicy!=null&&!Set.of("preserve","ignore").contains(d.loadConditionPolicy))throw new IllegalArgumentException("无效加载条件策略："+id);
            if(d.grid!=null&&(d.grid.size<1||d.grid.size>9||d.grid.cells==null||d.grid.cells.size()!=d.grid.size*d.grid.size))throw new IllegalArgumentException("配方画布网格无效："+id);
            if(d.source!=null&&!d.source.isJsonObject())throw new IllegalArgumentException("来源配方快照无效："+id);
            if (!d.operation.equals("remove") && (d.data == null || !d.data.has("type"))) throw new IllegalArgumentException("配方缺少类型：" + id);
        });
        scripts.forEach((path,d)->{
            if(d==null||!path.equals(d.path)||d.text==null||d.baseHash==null||!d.baseHash.matches("-|[a-f0-9]{64}"))throw new IllegalArgumentException("脚本草稿结构无效："+path);
            if(!path.matches("kubejs/(server_scripts|startup_scripts|client_scripts)/.+\\.js")||Arrays.stream(path.split("/",-1)).anyMatch(v->v.isEmpty()||v.equals(".")||v.equals("..")))throw new IllegalArgumentException("无效脚本草稿路径："+path);
        });
        items.forEach((id, d) -> { requireId(id); if (d == null || !id.equals(d.id)) throw new IllegalArgumentException("草稿物品 ID 不一致"); });
    }

    public static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("无效注册 ID：" + id);
        if(Arrays.stream(id.substring(id.indexOf(':')+1).split("/",-1)).anyMatch(s->s.equals(".")||s.equals("..")||s.isEmpty()))throw new IllegalArgumentException("注册路径不能包含空段、. 或 ..："+id);
    }
}
