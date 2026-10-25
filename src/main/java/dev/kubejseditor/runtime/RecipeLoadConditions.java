package dev.kubejseditor.runtime;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.recipe.LoadConditions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.neoforged.neoforge.common.conditions.*;
import java.util.*;

/** Uses native condition codecs and the current KJS reload's tag context, not field-name heuristics. */
public final class RecipeLoadConditions {
    public static boolean matches(JsonObject data){
        if(!LoadConditions.present(data))return true;
        try{
            var current=ScriptIndex.recipeOps();
            if(current!=null)return ICondition.conditionsMatched(current,data);
            var server=Minecraft.getInstance().getSingleplayerServer();if(server==null)throw new IllegalStateException("需要本机服务端校验加载条件");
            var registries=server.registryAccess();
            var context=new ICondition.IContext(){public <T> Map<ResourceLocation,Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> key){
                var result=new HashMap<ResourceLocation,Collection<Holder<T>>>();registries.registryOrThrow(key).getTags().forEach(pair->result.put(pair.getFirst().location(),pair.getSecond().stream().toList()));return result;
            }};
            return ICondition.conditionsMatched(new ConditionalOps<>(registries.createSerializationContext(JsonOps.INSTANCE),context),data);
        }catch(RuntimeException error){throw new IllegalArgumentException("加载条件解析失败（可切换强制忽略）："+error.getMessage(),error);}
    }
    private RecipeLoadConditions(){}
}
