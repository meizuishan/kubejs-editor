package dev.kubejseditor.runtime;

import com.google.gson.*;
import com.mojang.serialization.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.LoadConditions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import java.lang.reflect.Modifier;
import java.io.IOException;
import java.util.*;

/** Native datapack registries are separate from RecipeManager. No mod implementation is linked. */
public final class RegistryDataSources {
    public record Entry(RegistryEntryTarget target,JsonObject data,Object value){}
    private static final Map<String,RegistryEntryTarget> observed=new HashMap<>();
    private static String lastFailure="";
    public static String failure(){return lastFailure;}
    public static void clear(){observed.clear();declaredCodecs=null;}
    public static Set<String> knownIds(){return Set.copyOf(observed.keySet());}
    private static RegistryAccess access(){var mc=Minecraft.getInstance();return mc.hasSingleplayerServer()?mc.getSingleplayerServer().registryAccess():mc.level.registryAccess();}
    @SuppressWarnings({"rawtypes","unchecked"}) private static Registry registry(RegistryAccess access,String name){return access.registryOrThrow(ResourceKey.createRegistryKey(ResourceLocation.parse(name)));}
    /** Explicit extension for registries whose public codec is not named DIRECT_CODEC. */
    public static void registerCodec(String registry,String publicClass,String publicField){var target=new RegistryEntryTarget(registry,"kubejs_editor:codec");target.codecClass=publicClass;target.codecField=publicField;codecHints.put(registry,target);}
    private static final Map<String,RegistryEntryTarget> codecHints=new HashMap<>();
    private static Map<String,Codec<Object>> declaredCodecs;
    @SuppressWarnings("unchecked") private static Codec<Object> declaredCodec(String registry){if(declaredCodecs==null){var all=new HashMap<String,Codec<Object>>();net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().forEach(d->all.put(d.key().location().toString(),(Codec<Object>)d.elementCodec()));declaredCodecs=all;}return declaredCodecs.get(registry);}
    private static void identifyCodec(RegistryEntryTarget target,Object value)throws ReflectiveOperationException{
        if(declaredCodec(target.registry)!=null){target.codecSource="datapack";return;}
        if("datapack".equals(target.codecSource))throw new IllegalArgumentException("注册表数据接口已不可用："+target.registry);
        var hint=codecHints.get(target.registry);if(hint!=null){target.codecClass=hint.codecClass;target.codecField=hint.codecField;return;}
        if(target.codecClass==null){target.codecClass=value.getClass().getName();target.codecField="DIRECT_CODEC";}
        codec(target);
    }
    @SuppressWarnings("unchecked") public static Codec<Object> codec(RegistryEntryTarget target)throws ReflectiveOperationException{
        if("datapack".equals(target.codecSource)){var declared=declaredCodec(target.registry);if(declared==null)throw new IllegalArgumentException("注册表未公开数据编码器："+target.registry);return declared;}
        if(target.codecClass==null)throw new IllegalArgumentException("注册表未公开可验证的数据 Codec："+target.registry);
        var field=Class.forName(target.codecClass).getField(target.codecField);if(!Modifier.isStatic(field.getModifiers())||!Codec.class.isAssignableFrom(field.getType()))throw new IllegalArgumentException("需要公开的静态原生 Codec");return (Codec<Object>)field.get(null);
    }
    public static RegistryEntryTarget target(String id){
        var draft=EditorRuntime.project().recipes.get(id);if(draft!=null&&draft.registryTarget!=null)return draft.registryTarget;
        draft=EditorRuntime.state().applied.recipes.get(id);if(draft!=null&&draft.registryTarget!=null)return draft.registryTarget;
        var known=observed.get(id);return known==null?RegistryEntryTarget.parse(id):known;
    }
    @SuppressWarnings({"rawtypes","unchecked"}) public static List<RegistryEntryTarget> resolve(Object display){
        if(display instanceof Holder<?> holder)display=holder.value();final Object shown=display;var mc=Minecraft.getInstance();if(mc.level==null)return List.of();
        var result=new LinkedHashMap<String,RegistryEntryTarget>();lastFailure="";if(!mc.hasSingleplayerServer()){lastFailure="完整原生注册表编辑需要本机服务端；客户端同步可能省略参数";return List.of();}
        for(var registryAccess:List.of(mc.level.registryAccess(),access()))registryAccess.registries().forEach(entry->{try{String name=entry.key().location().toString();if(declaredCodec(name)==null&&!codecHints.containsKey(name))return;Registry client=entry.value();var id=client.getKey(shown);if(id==null||client.get(id)!=shown)return;var target=new RegistryEntryTarget(name,id.toString());identifyCodec(target,shown);var source=read(target);if(source==null)return;
            // The public registry identity establishes provenance. Network codecs may omit
            // server-only fields, so encoding a client projection with the data Codec is invalid.
            if(source.value.getClass()!=shown.getClass())throw new IllegalArgumentException("客户端和服务端注册表条目类型不同");
            observed.put(target.id(),source.target);result.put(target.id(),source.target);
        }catch(ReflectiveOperationException|RuntimeException error){lastFailure="原生注册表来源无法读取："+entry.key().location()+" · "+error.getMessage();}});
        return List.copyOf(result.values());
    }
    public static Entry read(String id){return read(target(id));}
    @SuppressWarnings({"unchecked","rawtypes"}) public static Entry read(RegistryEntryTarget target){
        if(target==null)return null;Object value=registry(access(),target.registry).get(ResourceLocation.parse(target.entry));if(value==null)return null;
        try{identifyCodec(target,value);var ops=access().createSerializationContext(JsonOps.INSTANCE);var encoded=codec(target).encodeStart(ops,value).getOrThrow().getAsJsonObject();JsonObject raw=encoded.deepCopy();
            var server=Minecraft.getInstance().getSingleplayerServer();if(server!=null){var file=ResourceLocation.fromNamespaceAndPath(ResourceLocation.parse(target.entry).getNamespace(),target.directory()+"/"+ResourceLocation.parse(target.entry).getPath()+".json");var resource=server.getResourceManager().getResource(file);if(resource.isPresent())try(var reader=resource.get().openAsReader()){raw=JsonParser.parseReader(reader).getAsJsonObject();}}
            Object decoded=codec(target).parse(ops,LoadConditions.body(raw)).getOrThrow();if(!codec(target).encodeStart(ops,decoded).getOrThrow().equals(encoded))throw new IllegalArgumentException("原始资源与已加载注册表内容不一致："+target.entry);
            target.syntheticType=!raw.has("type");if(target.syntheticType)raw.addProperty("type",target.registry);observed.put(target.id(),target);return new Entry(target,raw,value);
        }catch(ReflectiveOperationException|IOException error){throw new IllegalArgumentException("无法读取原生注册表："+target.registry+" / "+target.entry,error);}
    }
    public static Object decode(RegistryEntryTarget target,JsonObject data){try{return codec(target).parse(access().createSerializationContext(JsonOps.INSTANCE),LoadConditions.body(target.body(data))).getOrThrow();}catch(ReflectiveOperationException e){throw new IllegalArgumentException("原生 Codec 不可用",e);}}
    public static boolean shouldExist(EditorProject.RecipeDraft draft){
        if(draft.operation.equals("remove"))return false;
        var data=draft.registryTarget.generated(draft);if(!LoadConditions.present(data))return true;
        var ops=new net.neoforged.neoforge.common.conditions.ConditionalOps<>(access().createSerializationContext(JsonOps.INSTANCE),net.neoforged.neoforge.common.conditions.ICondition.IContext.TAGS_INVALID);
        return net.neoforged.neoforge.common.conditions.ICondition.conditionsMatched(ops,data);
    }
    public static void validate(EditorProject.RecipeDraft draft){draft.registryTarget.check();if(!draft.id.equals(draft.registryTarget.id()))throw new IllegalArgumentException("注册表目标与 ID 不一致");if(draft.operation.equals("add")&&!EditorRuntime.state().applied.recipes.containsKey(draft.id)&&registry(access(),draft.registryTarget.registry).containsKey(ResourceLocation.parse(draft.registryTarget.entry)))throw new IllegalArgumentException("注册表条目 ID 已被占用："+draft.registryTarget.entry);if(shouldExist(draft))decode(draft.registryTarget,draft.data);}
    public static void verify(EditorProject.RecipeDraft draft,boolean exists)throws IOException{
        try{Object actual=registry(access(),draft.registryTarget.registry).get(ResourceLocation.parse(draft.registryTarget.entry));if(!exists){if(actual!=null)throw new IOException("注册表条目未按预期禁用："+draft.registryTarget.entry);return;}if(actual==null)throw new IOException("注册表条目未加载："+draft.registryTarget.entry);
            var ops=access().createSerializationContext(JsonOps.INSTANCE);var codec=codec(draft.registryTarget);if(!codec.encodeStart(ops,decode(draft.registryTarget,draft.data)).getOrThrow().equals(codec.encodeStart(ops,actual).getOrThrow()))throw new IOException("注册表实际内容与预期不一致："+draft.registryTarget.entry);
        }catch(ReflectiveOperationException|RuntimeException e){throw new IOException("注册表生效核对失败："+draft.id+" · "+e.getMessage(),e);}
    }
    private RegistryDataSources(){}
}
