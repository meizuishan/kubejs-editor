package dev.kubejseditor.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** JEI's registered type/codec contract, including types unknown to this mod. */
public final class CompatIngredientTypes {
    public static List<IIngredientType<?>> others(){if(WorkspaceJeiPlugin.runtime==null)return List.of();return WorkspaceJeiPlugin.runtime.getIngredientManager().getRegisteredIngredientTypes().stream().filter(t->!ItemStack.class.isAssignableFrom(t.getIngredientClass())&&!FluidStack.class.isAssignableFrom(t.getIngredientClass())).toList();}
    public static IIngredientType<?> find(String id){return others().stream().filter(t->t.getUid().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("JEI 未注册原料类型："+id));}
    public static boolean accepts(String id,Object value){try{return find(id).getIngredientClass().isInstance(value);}catch(RuntimeException e){return false;}}
    @SuppressWarnings({"unchecked","rawtypes"}) public static JsonElement encode(String id,Object value){var type=find(id);if(!type.getIngredientClass().isInstance(value))throw new IllegalArgumentException("需要其他原料类型："+id);var ops=Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);return (JsonElement)WorkspaceJeiPlugin.runtime.getIngredientManager().getIngredientCodec((IIngredientType)type).encodeStart(ops,value).getOrThrow();}
    @SuppressWarnings({"unchecked","rawtypes"}) public static Object decode(String id,JsonElement value){var ops=Minecraft.getInstance().level.registryAccess().createSerializationContext(JsonOps.INSTANCE);return WorkspaceJeiPlugin.runtime.getIngredientManager().getIngredientCodec((IIngredientType)find(id)).parse(ops,value).getOrThrow();}
    @SuppressWarnings({"unchecked","rawtypes"}) public static Collection<?> values(String id){return WorkspaceJeiPlugin.runtime.getIngredientManager().getAllIngredients((IIngredientType)find(id));}
    public static String signature(Object value){for(var type:others())if(type.getIngredientClass().isInstance(value))return "other:"+type.getUid()+":"+dev.kubejseditor.recipe.compat.CompatJson.canonical(encode(type.getUid(),value));throw new IllegalArgumentException("该 JEI 原料未提供编码");}
    private CompatIngredientTypes(){}
}
