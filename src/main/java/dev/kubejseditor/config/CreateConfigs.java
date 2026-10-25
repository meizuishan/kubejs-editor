package dev.kubejseditor.config;

import com.electronwill.nightconfig.core.*;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.neoforged.fml.config.*;
import net.neoforged.neoforge.common.ModConfigSpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Edits the loaded config's actual path, including world-scoped server configs. */
public final class CreateConfigs {
    public record Entry(ModConfig config, Path file, List<String> path, ModConfigSpec.ValueSpec spec, Object current) {
        public String key() { return config.getModId()+" / "+String.join(".",path); }
        public String scope() { return config.getType()+" · "+file; }
        public String text(Object value){return value instanceof List<?>?dev.kubejseditor.storage.ProjectStore.JSON.toJson(value):value instanceof Enum<?> e?e.name():String.valueOf(value);}
        public Class<?> valueType(){return ConfigValueTypes.resolve(spec.getRange()==null?null:spec.getRange().getClazz(),spec.getClazz(),spec.getDefault(),current);}
        public Object parse(String text) {
            Class<?> type=valueType(); Object value;
            if(List.class.isAssignableFrom(type)) {
                var array=com.google.gson.JsonParser.parseString(text).getAsJsonArray();List<Object> list=new ArrayList<>();
                List<?> defaults=spec.getDefault() instanceof List<?> l?l:List.of();Object example=!defaults.isEmpty()?defaults.getFirst():current instanceof List<?> l&&!l.isEmpty()?l.getFirst():"";
                for(var e:array){if(!e.isJsonPrimitive())throw new IllegalArgumentException("列表仅支持普通字符串、数值和布尔值");list.add(ConfigValueTypes.scalar(example.getClass(),e.getAsString()));}value=list;
            }
            else value=ConfigValueTypes.scalar(type,text);
            if(!spec.test(value))throw new IllegalArgumentException("配置值超出模组允许范围："+spec.getRange());return value;
        }
        public boolean editable() {return ConfigValueTypes.supported(valueType());}
    }
    public record Pending(Entry entry,Object value) {}
    private static final List<Pending> PENDING=new ArrayList<>();
    private static final List<dev.kubejseditor.model.EditorProject.ConfigDraft> UNRESOLVED=new ArrayList<>();
    public static List<Pending> pending(){return List.copyOf(PENDING);}
    public static void clear(){PENDING.clear();UNRESOLVED.clear();}
    public static void persistInto(dev.kubejseditor.model.EditorProject project){
        project.configs=new ArrayList<>(UNRESOLVED);
        for(Pending p:PENDING){var draft=new dev.kubejseditor.model.EditorProject.ConfigDraft();draft.modId=p.entry.config.getModId();draft.file=p.entry.config.getFileName();draft.type=p.entry.config.getType().name();draft.path=p.entry.path;draft.value=p.entry.text(p.value);draft.target=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize().relativize(p.entry.file).toString();project.configs.add(draft);}
    }
    public static void restore(dev.kubejseditor.model.EditorProject project){
        clear();if(project.configs==null||project.configs.isEmpty())return;List<Entry> loaded=all();
        Path root=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        for(var d:project.configs){var e=loaded.stream().filter(v->v.config.getModId().equals(d.modId)&&v.config.getFileName().equals(d.file)&&v.config.getType().name().equals(d.type)&&v.path.equals(d.path)&&(d.target==null||v.file.equals(root.resolve(d.target).normalize()))).findFirst();
            if(e.isPresent())try{queue(e.get(),d.value);}catch(Exception ignored){UNRESOLVED.add(d);}else UNRESOLVED.add(d);
        }
    }
    public static void queue(Entry e,String text){Object value=e.parse(text);PENDING.removeIf(p->p.entry.config==e.config&&p.entry.file.equals(e.file)&&p.entry.path.equals(e.path));PENDING.add(new Pending(e,value));}
    public static List<Entry> list() {
        return all().stream().filter(e->isCreate(e.config.getModId())).toList();
    }
    public static List<Entry> forMod(String mod){return all().stream().filter(e->e.config.getModId().equals(mod)).toList();}
    public static List<Entry> all() {
        List<Entry> result=new ArrayList<>();
            for(ModConfig.Type type:ModConfig.Type.values())for(ModConfig config:ModConfigs.getConfigSet(type)) {
                if(config.getLoadedConfig()==null||config.getFullPath()==null||!(config.getSpec() instanceof ModConfigSpec spec))continue;
                walk(config,spec.getSpec(),new ArrayList<>(),result);
            }
        result.sort(Comparator.comparing(Entry::key));return result;
    }
    private static boolean isCreate(String id){return dev.kubejseditor.module.EditorModules.ownsCreateConfig(id);}
    private static void walk(ModConfig mod,UnmodifiableConfig tree,List<String> path,List<Entry> out) {
        for(var e:tree.valueMap().entrySet()) {
            List<String> child=new ArrayList<>(path);child.add(e.getKey());
            if(e.getValue() instanceof ModConfigSpec.ValueSpec value)out.add(new Entry(mod,mod.getFullPath().toAbsolutePath().normalize(),List.copyOf(child),value,mod.getLoadedConfig().config().get(child)));
            else if(e.getValue() instanceof UnmodifiableConfig nested)walk(mod,nested,child,out);
        }
    }
    public static Map<String,byte[]> output(Path root) {
        if(!UNRESOLVED.isEmpty())throw new IllegalArgumentException("存在未加载或已失效的配置草稿；请加载对应模组，或在模组参数页清空参数更改");
        Map<ModConfig,CommentedConfig> copies=new LinkedHashMap<>();
        for(Pending pending:PENDING){Entry e=pending.entry;
            if(!e.file.equals(e.config.getFullPath().toAbsolutePath().normalize()))throw new IllegalArgumentException("参数所属世界已改变，请重新选择："+e.key());
            if(!e.spec.test(pending.value))throw new IllegalArgumentException("配置值已失效："+e.key());
            CommentedConfig copy=copies.computeIfAbsent(e.config,c->deepCopy(c.getLoadedConfig().config()));copy.set(e.path,pending.value);
        }
        Map<String,byte[]> output=new LinkedHashMap<>();
        for(var e:copies.entrySet()){
            Path file=e.getKey().getFullPath().toAbsolutePath().normalize();Path base=root.toAbsolutePath().normalize();
            if(!file.startsWith(base))throw new IllegalArgumentException("配置文件位于实例之外："+file);
            output.put(base.relativize(file).toString(),new TomlWriter().writeToString(e.getValue()).getBytes(StandardCharsets.UTF_8));
        }
        return output;
    }
    private static CommentedConfig deepCopy(UnmodifiableCommentedConfig source){
        CommentedConfig copy=CommentedConfig.inMemory();
        for(var e:source.valueMap().entrySet())copy.set(List.of(e.getKey()),e.getValue() instanceof UnmodifiableCommentedConfig nested?deepCopy(nested):e.getValue());
        copy.putAllComments(source);return copy;
    }
}
