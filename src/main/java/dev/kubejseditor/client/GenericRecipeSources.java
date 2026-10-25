package dev.kubejseditor.client;

import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.export.GenericFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** Public wrapper identities and full native-codec copies, without mod or machine names. */
public final class GenericRecipeSources {
    public record Option(ResourceLocation id,int score,String method,String notice){}
    private static final Map<RecipeSerializer<?>,Map<String,List<ResourceLocation>>> encoded=new IdentityHashMap<>();
    private static RecipeManager lastManager;private static long generation;
    public static void clear(){encoded.clear();lastManager=null;}
    @SuppressWarnings({"rawtypes","unchecked"}) public static List<Option> inspect(Object display,RecipeManager manager,Map<Object,Set<ResourceLocation>> identities){
        if(lastManager!=manager||generation!=ScriptIndex.serverGeneration){clear();lastManager=manager;generation=ScriptIndex.serverGeneration;}
        var graph=PublicObjectGraph.scan(display,t->Recipe.class.isAssignableFrom(t)||RecipeHolder.class.isAssignableFrom(t)||Holder.class.isAssignableFrom(t));var found=new LinkedHashMap<ResourceLocation,Option>();
        for(Object value:graph){
            if(value instanceof RecipeHolder<?> holder&&manager.byKey(holder.id()).isPresent())put(found,new Option(holder.id(),1000,"真实配方 ID",""));
            var ids=identities.get(value);if(ids!=null)for(var id:ids)put(found,new Option(id,1000,"公开对象来源",""));
        }
        if(!found.isEmpty())return ranked(found.values());
        for(Object value:graph)if(!(value instanceof Recipe<?>)&&!(value instanceof RecipeHolder<?>))for(var target:RegistryDataSources.resolve(value))put(found,new Option(ResourceLocation.parse(target.id()),990,"数据包注册表","这是原生数据包注册表条目；修改后需要重启。来源："+target.entry));
        if(!found.isEmpty())return ranked(found.values());
        if(!Minecraft.getInstance().hasSingleplayerServer())return List.of();
        for(Object value:graph)if(value instanceof Recipe<?> recipe)try{
            String signature=signature(recipe);var byData=encoded.computeIfAbsent(recipe.getSerializer(),serializer->{var result=new HashMap<String,List<ResourceLocation>>();for(var source:manager.getRecipes())if(source.value().getSerializer()==serializer)try{result.computeIfAbsent(signature(source.value()),k->new ArrayList<>()).add(source.id());}catch(RuntimeException ignored){}return result;});
            for(var id:byData.getOrDefault(signature,List.of()))put(found,new Option(id,940,"完整原生编码匹配","JEI 展示是配方副本或包装；编辑实际来源会影响所有使用该来源的机器。已核对完整原生编码；编辑时保留来源的组件、数量及其他字段。"));
        }catch(RuntimeException ignored){}
        return ranked(found.values());
    }
    private static String signature(Recipe<?> value){return CompatJson.canonical(GenericFormatter.encodeRecipe(new RecipeHolder<>(ResourceLocation.parse("kubejs_editor:source_probe"),value))).toString();}
    public static List<Option> ranked(Collection<Option> values){return values.stream().sorted(Comparator.comparingInt(Option::score).reversed().thenComparing(o->o.id.toString())).toList();}
    public static void put(Map<ResourceLocation,Option> found,Option next){found.merge(next.id,next,(a,b)->a.score>=b.score?a:b);}
    public static JeiRecipeSources.Source select(Collection<Option> values){var ranked=ranked(values);if(ranked.isEmpty())return null;var best=ranked.stream().filter(v->v.score==ranked.getFirst().score).toList();if(best.size()>1)return new JeiRecipeSources.Source(null,"多个来源具有相同的完整匹配依据，请确认；不会按产物或模组名称猜测",best.stream().map(Option::id).toList());var winner=best.getFirst();return new JeiRecipeSources.Source(winner.id,null,List.of(),winner.notice);
    }
    private GenericRecipeSources(){}
}
