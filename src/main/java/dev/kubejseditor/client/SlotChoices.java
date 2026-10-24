package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.recipe.RecipeCanvas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;
import java.util.function.Consumer;

/** Uses live registries and tags rather than a bundled list of modded IDs. */
public final class SlotChoices {
    public static List<ChoiceScreen.Choice> resources(RecipeCanvas.Slot slot,Consumer<Object> accept){
        List<ChoiceScreen.Choice> choices=new ArrayList<>();
        if(slot.fluid())for(var fluid:BuiltInRegistries.FLUID){
            if(fluid.defaultFluidState().isEmpty()||!fluid.defaultFluidState().isSource())continue;
            var stack=new FluidStack(fluid,250);String id=BuiltInRegistries.FLUID.getKey(fluid).toString();
            choices.add(new ChoiceScreen.Choice(stack.getHoverName().getString()+" · "+id,()->accept.accept(stack),"mB"));
        }else for(var item:BuiltInRegistries.ITEM){
            if(item==Items.AIR)continue;String id=BuiltInRegistries.ITEM.getKey(item).toString();
            choices.add(new ChoiceScreen.Choice(item.getDescription().getString()+" · "+id,()->accept.accept(new ItemStack(item))));
        }
        choices.sort(Comparator.comparing(ChoiceScreen.Choice::text));return choices;
    }
    public static List<ChoiceScreen.Choice> tags(RecipeCanvas.Slot slot,Consumer<String> accept){
        JsonObject o=slot.get().isJsonObject()?slot.get().getAsJsonObject():new JsonObject();
        String value=o.has("items")?o.get("items").getAsString():o.has("tag")?"#"+o.get("tag").getAsString():o.has("item")?o.get("item").getAsString():o.has("fluid")?o.get("fluid").getAsString():"";
        Set<String> tags=new TreeSet<>();
        if(value.startsWith("#")){tags.add(value.substring(1));}
        else {var id=ResourceLocation.tryParse(value);if(id!=null){
            if(slot.fluid())BuiltInRegistries.FLUID.get(id).builtInRegistryHolder().tags().forEach(tag->tags.add(tag.location().toString()));
            else BuiltInRegistries.ITEM.get(id).builtInRegistryHolder().tags().forEach(tag->tags.add(tag.location().toString()));
        }}
        return tags.stream().map(tag->new ChoiceScreen.Choice("#"+tag,()->accept.accept(tag))).toList();
    }
    private SlotChoices(){}
}
