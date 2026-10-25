package dev.kubejseditor.client;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.recipe.compat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import java.util.*;

/** Compares all alternatives, including components and quantities, rather than JEI's rotating icon. */
public final class CompatIngredients {
    private static final String QUANTITY="|quantity=";
    public static List<String> signatures(Collection<?> values){return signatures(values,null);}
    private static List<String> signatures(Collection<?> values,java.math.BigDecimal quantity){var result=new TreeSet<String>();var mc=Minecraft.getInstance();if(mc.level==null)return List.of();var ops=mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for(Object value:values)try{if(value instanceof ItemStack item&&!item.isEmpty())result.add("item:"+CompatJson.canonical(ItemStack.CODEC.encodeStart(ops,item.copyWithCount(1)).getOrThrow())+QUANTITY+number(quantity==null?java.math.BigDecimal.valueOf(item.getCount()):quantity));else if(value instanceof FluidStack fluid&&!fluid.isEmpty()){var sample=fluid.copy();sample.setAmount(1);result.add("fluid:"+CompatJson.canonical(FluidStack.CODEC.encodeStart(ops,sample).getOrThrow())+QUANTITY+number(quantity==null?java.math.BigDecimal.valueOf(fluid.getAmount()):quantity));}else result.add(CompatIngredientTypes.signature(value));}catch(RuntimeException ignored){}return List.copyOf(result);}
    private static String number(java.math.BigDecimal number){return number.stripTrailingZeros().toPlainString();}
    public static List<String> identities(List<String> signatures){return signatures.stream().map(s->{int index=s.lastIndexOf(QUANTITY);return index<0?s:s.substring(0,index);}).distinct().sorted().toList();}
    public static List<String> binding(RecipePresentation p,RecipePresentation.Binding b){try{java.math.BigDecimal quantity=b.field().kind==CompatDefinition.Kind.OTHER?null:p.item(b).get(b.fluid()?"amount":"count").getAsBigDecimal();return signatures(values(p,b),b.field().displayQuantity?quantity:java.math.BigDecimal.ONE);}catch(RuntimeException e){return List.of();}}
    public static List<Object> values(RecipePresentation p,RecipePresentation.Binding b){try{if(b.field().kind==CompatDefinition.Kind.OTHER){var raw=p.nativeValue(b);if(!b.field().valuePath.isBlank())raw=CompatJson.get(raw,b.field().valuePath);return List.of(CompatIngredientTypes.decode(b.field().ingredientType,raw));}var canonical=p.item(b);int count=1;var mc=Minecraft.getInstance();var ops=mc.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);var values=new ArrayList<Object>();
        if(b.output()||canonical.has("id")){var value=canonical.deepCopy();if(value.has("item"))value.add("id",value.remove("item"));if(b.fluid()){if(value.has("fluid"))value.add("id",value.remove("fluid"));value.addProperty("amount",1);values.add(FluidStack.CODEC.parse(ops,value).getOrThrow());}else {value.addProperty("count",1);values.add(ItemStack.CODEC.parse(ops,value).getOrThrow());}}
        else {JsonElement ingredient=canonical.has("alternatives")?canonical.get("alternatives"):canonical.deepCopy();if(ingredient.isJsonObject()){var o=ingredient.getAsJsonObject();o.remove("count");o.remove("amount");o.remove("chance");if(!b.fluid()&&o.has("id"))o.add("item",o.remove("id"));}
            if(b.fluid()){if(ingredient.isJsonObject()&&!ingredient.getAsJsonObject().has("type"))ingredient.getAsJsonObject().addProperty("type",ingredient.getAsJsonObject().has("tag")?"neoforge:tag":"neoforge:single");for(var value:FluidIngredient.CODEC.parse(ops,ingredient).getOrThrow().getStacks()){var copy=value.copy();copy.setAmount(count);values.add(copy);}}else for(var value:Ingredient.CODEC_NONEMPTY.parse(ops,ingredient).getOrThrow().getItems()){var copy=value.copy();copy.setCount(count);values.add(copy);}}
        return List.copyOf(values);
    }catch(RuntimeException error){return List.of();}}
    private CompatIngredients(){}
}
