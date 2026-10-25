package dev.kubejseditor.client;

import dev.kubejseditor.runtime.*;
import dev.kubejseditor.recipe.SimpleRecipes;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Machine drops navigate actual JEI catalyst associations, including multi-category machines. */
public final class CompatMachines {
    public static void choose(Screen parent,ItemStack machine){var mc=Minecraft.getInstance();if(WorkspaceJeiPlugin.runtime==null)throw new IllegalStateException("JEI 尚未准备好");var manager=WorkspaceJeiPlugin.runtime.getRecipeManager();var choices=new ArrayList<ChoiceScreen.Choice>();for(var category:manager.createRecipeCategoryLookup().get().toList()){boolean match=manager.createRecipeCatalystLookup(category.getRecipeType()).get().anyMatch(v->v.getIngredient() instanceof ItemStack item&&ItemStack.isSameItemSameComponents(item,machine));if(match)choices.add(new ChoiceScreen.Choice(category.getTitle().getString()+" · "+category.getRecipeType().getUid(),()->recipes(parent,category)));}if(choices.isEmpty())mc.setScreen(new TextScreen(parent,"机器关联", "JEI 没有为此物品登记加工分类。请从实际配方旁的编辑按钮进入；机器图标不能代替来源配方。"));else mc.setScreen(new ChoiceScreen(parent,"选择机器加工分类",choices));}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void recipes(Screen parent,IRecipeCategory category){var mc=Minecraft.getInstance();try{if(!SimpleRecipes.enabled())SimpleRecipes.setEnabled(EditorRuntime.root(),true);EditorRuntime.invalidateRecipes();var manager=WorkspaceJeiPlugin.runtime.getRecipeManager();var choices=new ArrayList<ChoiceScreen.Choice>();for(Object value:(List<?>)manager.createRecipeLookup(category.getRecipeType()).get().toList()){var layout=manager.createRecipeLayoutDrawable(category,value,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());if(layout.isEmpty())continue;var l=(mezz.jei.api.gui.IRecipeLayoutDrawable<?>)layout.get();var source=JeiRecipeSources.resolve(l);if(source.id()==null){choices.add(new ChoiceScreen.Choice("[展示 / 确认来源] "+JeiRecipeSources.title(l)+" · "+choices.size(),()->JeiDisplayScreen.open(parent,l),JeiRecipeSources.diagnostics(l,source.reason())));continue;}String id=source.id().toString();var r=EditorRuntime.recipe(id);if(r!=null){JeiRecipeSources.capture(l,source.id());choices.add(new ChoiceScreen.Choice(RecipeNames.main(r.data())+" · "+id,()->JeiDisplayScreen.open(parent,l),r.capabilityLabel()+" · "+(r.error()==null?"":r.error())));}}mc.setScreen(new ChoiceScreen(parent,"选择配方（修改影响所有使用此配方的机器）",choices));}catch(Exception e){mc.setScreen(new TextScreen(parent,"读取机器配方失败",e.toString()));}}
    private CompatMachines(){}
}
