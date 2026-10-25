package dev.kubejseditor.verification;

import mezz.jei.api.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import java.util.*;

/** Public nested wrapper, with no exposed source ID. The production resolver has no fixture knowledge. */
@JeiPlugin public final class PatchVerificationJei implements IModPlugin {
    public record Box(Object recipe){}
    public record Frame(Optional<Box> payload){}
    public static final RecipeType<Frame> TYPE=new RecipeType<>(ResourceLocation.parse("kubejs_editor_verification:wrapped_patch"),Frame.class);
    public ResourceLocation getPluginUid(){return ResourceLocation.parse("kubejs_editor_verification:patch_jei");}
    public void registerCategories(IRecipeCategoryRegistration registration){if(!Boolean.getBoolean("kubejs.editor.verifyPatches"))return;var icon=registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(new ItemStack(Items.FURNACE));registration.addRecipeCategories(new IRecipeCategory<Frame>(){public RecipeType<Frame> getRecipeType(){return TYPE;}public Component getTitle(){return Component.literal("Generic wrapped data");}public int getWidth(){return 116;}public int getHeight(){return 46;}public IDrawable getIcon(){return icon;}public void setRecipe(IRecipeLayoutBuilder b,Frame frame,IFocusGroup focus){Object value=frame.payload.orElseThrow().recipe;if(value instanceof net.minecraft.world.item.crafting.RecipeHolder<?> h)value=h.value();var recipe=(GenericCompatFixture.GenericRecipe)value;b.addSlot(RecipeIngredientRole.INPUT,4,10).addIngredients(recipe.feedstock().ingredient());b.addSlot(RecipeIngredientRole.INPUT,40,10).addIngredientsUnsafe(Arrays.asList(recipe.bath().getFluids()));b.addSlot(RecipeIngredientRole.OUTPUT,92,10).addItemStack(recipe.product());}});}
}
