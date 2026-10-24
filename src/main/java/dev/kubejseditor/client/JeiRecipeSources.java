package dev.kubejseditor.client;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import dev.kubejseditor.recipe.SimpleRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Uses only exported JEI interfaces. New source-ID APIs are optional on older JEI releases. */
public final class JeiRecipeSources {
    public record Source(ResourceLocation id,String reason){}
    public static <T> Source resolve(IRecipeLayoutDrawable<T> layout){
        var mc=Minecraft.getInstance();if(mc.level==null)return new Source(null,"世界尚未加载");
        var manager=mc.hasSingleplayerServer()?mc.getSingleplayerServer().getRecipeManager():mc.level.getRecipeManager();
        if(layout.getRecipe() instanceof RecipeHolder<?> holder&&manager.byKey(holder.id()).isPresent())return new Source(holder.id(),null);
        var ids=new LinkedHashSet<ResourceLocation>();
        try{
            var method=IRecipeLayoutDrawable.class.getMethod("getSourceRecipeIds");Object value=method.invoke(layout);
            if(value instanceof List<?> list)for(Object id:list)if(id instanceof ResourceLocation location)ids.add(location);
        }catch(NoSuchMethodException ignored){/* JEI 19.27 has no source-ID API. */}catch(ReflectiveOperationException|LinkageError ignored){/* Public API unavailable; use verified legacy identity below. */}
        if(ids.isEmpty()){var legacy=layout.getRecipeCategory().getRegistryName(layout.getRecipe());if(legacy!=null)ids.add(legacy);}
        // Older machine plugins often register Recipe values without their RecipeHolder IDs.
        // Resolve only actual manager object identity; never infer an ID from the shown output.
        if(ids.isEmpty()&&layout.getRecipe() instanceof Recipe<?> recipe){
            for(var holder:mc.level.getRecipeManager().getRecipes())if(holder.value()==recipe&&manager.byKey(holder.id()).isPresent())ids.add(holder.id());
            if(mc.hasSingleplayerServer())for(var holder:manager.getRecipes())if(holder.value()==recipe)ids.add(holder.id());
        }
        if(ids.size()!=1)return new Source(null,ids.isEmpty()?"JEI 展示未提供实际配方来源":"JEI 展示对应多个来源，不能自动选择其中一条");
        var id=ids.getFirst();return manager.byKey(id).isPresent()?new Source(id,null):new Source(null,"展示 ID 不属于原生 RecipeManager，不能直接写为机器配方："+id);
    }
    public static <T> void capture(IRecipeLayoutDrawable<T> layout,ResourceLocation id){
        int inputs=0,outputs=0,fluidInputs=0,fluidOutputs=0;
        for(var slot:layout.getRecipeSlotsView().getSlotViews()){
            boolean input=slot.getRole()==RecipeIngredientRole.INPUT,output=slot.getRole()==RecipeIngredientRole.OUTPUT;if(!input&&!output)continue;
            var ingredients=slot.getAllIngredients().toList();if(ingredients.isEmpty())continue;
            boolean fluid=ingredients.stream().anyMatch(ingredient->ingredient.getIngredient() instanceof FluidStack);
            if(input){inputs++;if(fluid)fluidInputs++;}else{outputs++;if(fluid)fluidOutputs++;}
        }
        SimpleRecipes.record(id.toString(),new SimpleRecipes.JeiEvidence(layout.getRecipeCategory().getRecipeType().getUid().toString(),layout.getRecipeCategory().getTitle().getString(),inputs,outputs,fluidInputs,fluidOutputs));
    }
    public static <T> String diagnostics(IRecipeLayoutDrawable<T> layout,String reason){
        return reason+"\n\nJEI 分类："+layout.getRecipeCategory().getRecipeType().getUid()+"\n"+layout.getRecipeCategory().getTitle().getString()+"\n\n简单适配需要唯一的实际配方来源；JEI 燃料、动态计算或多来源展示需专用接口。";
    }
    private JeiRecipeSources(){}
}
