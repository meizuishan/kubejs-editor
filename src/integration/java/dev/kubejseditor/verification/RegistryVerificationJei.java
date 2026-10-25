package dev.kubejseditor.verification;

import mezz.jei.api.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

@JeiPlugin public final class RegistryVerificationJei implements IModPlugin {
    public static final RecipeType<RegistryVerificationFixture.Program> TYPE=new RecipeType<>(ResourceLocation.parse("kubejs_editor_verification:registry_process"),RegistryVerificationFixture.Program.class);
    public ResourceLocation getPluginUid(){return ResourceLocation.parse("kubejs_editor_verification:registry_jei");}
    public void registerCategories(IRecipeCategoryRegistration registration){if(!Boolean.getBoolean("kubejs.editor.verifyRegistry")&&!Boolean.getBoolean("kubejs.editor.verifyUiRevision"))return;var gui=registration.getJeiHelpers().getGuiHelper();var background=gui.createBlankDrawable(112,42);var icon=gui.createDrawableItemStack(new ItemStack(Items.GOLD_INGOT));registration.addRecipeCategories(new IRecipeCategory<RegistryVerificationFixture.Program>(){
        public RecipeType<RegistryVerificationFixture.Program> getRecipeType(){return TYPE;}
        public Component getTitle(){return Component.literal("Generic registry process");}
        public IDrawable getBackground(){return background;}
        public IDrawable getIcon(){return icon;}
        public void setRecipe(IRecipeLayoutBuilder builder,RegistryVerificationFixture.Program value,IFocusGroup focuses){builder.addSlot(RecipeIngredientRole.INPUT,8,10).setSlotName("input").addIngredients(value.input());builder.addSlot(RecipeIngredientRole.OUTPUT,78,10).setSlotName("result").addItemStack(value.result());}
    });}
    public void registerRecipes(IRecipeRegistration registration){if(Boolean.getBoolean("kubejs.editor.verifyRegistry")||Boolean.getBoolean("kubejs.editor.verifyUiRevision"))registration.addRecipes(TYPE,Minecraft.getInstance().level.registryAccess().registryOrThrow(RegistryVerificationFixture.KEY).stream().toList());}
}
