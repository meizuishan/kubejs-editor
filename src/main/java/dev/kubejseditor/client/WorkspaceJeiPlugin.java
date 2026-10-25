package dev.kubejseditor.client;

import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IAdvancedRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Adapted from Astronomy20's MIT-licensed RecipeBuilderGhostHandler. */
@JeiPlugin
public final class WorkspaceJeiPlugin implements IModPlugin {
    public static mezz.jei.api.runtime.IJeiRuntime runtime;
    private static long rebuildStarted;
    public static long lastRebuildMs;
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value){runtime=value;if(rebuildStarted!=0){lastRebuildMs=(System.nanoTime()-rebuildStarted)/1_000_000;rebuildStarted=0;}dev.kubejseditor.runtime.EditorRuntime.invalidateRecipes();}
    @Override public void onRuntimeUnavailable(){rebuildStarted=System.nanoTime();runtime=null;JeiRecipeButtons.clear();CompatJeiLayouts.clear();JeiRecipeSources.clear();dev.kubejseditor.recipe.SimpleRecipes.clearJeiEvidence();dev.kubejseditor.runtime.EditorRuntime.invalidateRecipes();}
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("kubejs_editor","workspace");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiScreenHandler(CompatibilityScreen.class,screen->{if(screen.width<2||screen.height<2)return null;int screenWidth=screen.width,screenHeight=screen.height,guiWidth=Math.max(1,screen.width-112);return new IGuiProperties(){
            public Class<CompatibilityScreen> screenClass(){return CompatibilityScreen.class;}
            public int guiLeft(){return 0;}public int guiTop(){return 0;}
            public int guiXSize(){return guiWidth;}public int guiYSize(){return screenHeight;}
            public int screenWidth(){return screenWidth;}public int screenHeight(){return screenHeight;}
        };});
        registration.addGuiScreenHandler(JeiDisplayScreen.class,screen->{if(screen.width<2||screen.height<2)return null;int w=screen.width,h=screen.height;return new IGuiProperties(){public Class<JeiDisplayScreen> screenClass(){return JeiDisplayScreen.class;}public int guiLeft(){return 0;}public int guiTop(){return 0;}public int guiXSize(){return Math.max(280,w-112);}public int guiYSize(){return h;}public int screenWidth(){return w;}public int screenHeight(){return h;}};});
        registration.addGhostIngredientHandler(CompatibilityScreen.class,compatibilityGhostHandler());
        registration.addGuiScreenHandler(FieldPatchScreen.class,screen->{int w=screen.width,h=screen.height;return new IGuiProperties(){public Class<FieldPatchScreen> screenClass(){return FieldPatchScreen.class;}public int guiLeft(){return 0;}public int guiTop(){return 0;}public int guiXSize(){return Math.max(280,w-112);}public int guiYSize(){return h;}public int screenWidth(){return w;}public int screenHeight(){return h;}};});
        registration.addGhostIngredientHandler(FieldPatchScreen.class,patchGhostHandler());
        registration.addGuiScreenHandler(WorkspaceScreen.class,screen->{
            if(screen.width<2||screen.height<2||screen.guiBounds().getWidth()<2||screen.guiBounds().getHeight()<2)return null;
            // JEI compares the previous properties to the new snapshot after GUI resizing.
            // Reading live fields from the previous object hides that size change.
            var bounds=screen.guiBounds();int fullWidth=screen.width,fullHeight=screen.height;
            return new IGuiProperties(){
            public Class<WorkspaceScreen> screenClass(){return WorkspaceScreen.class;}
            public int guiLeft(){return bounds.getX();}public int guiTop(){return bounds.getY();}
            public int guiXSize(){return bounds.getWidth();}public int guiYSize(){return bounds.getHeight();}
            public int screenWidth(){return fullWidth;}public int screenHeight(){return fullHeight;}
        };});
        registration.addGhostIngredientHandler(WorkspaceScreen.class,new IGhostIngredientHandler<WorkspaceScreen>(){
            @Override public <I> List<Target<I>> getTargetsTyped(WorkspaceScreen gui,ITypedIngredient<I> ingredient,boolean start){
                Object value=ingredient.getIngredient();if(!(value instanceof ItemStack)&&!(value instanceof FluidStack))return List.of();
                List<Target<I>> targets=new ArrayList<>();for(var target:gui.ghostTargets()){
                    if(value instanceof ItemStack&&target.fluid())continue;
                    targets.add(new Target<>(){
                        @Override public Rect2i getArea(){return target.area();}
                        @Override public void accept(I dropped){target.accept().accept(dropped);}
                    });
                }return targets;
            }
            @Override public void onComplete(){}
        });
    }
    public static IGhostIngredientHandler<CompatibilityScreen> compatibilityGhostHandler(){return new IGhostIngredientHandler<CompatibilityScreen>(){
            public <I> List<Target<I>> getTargetsTyped(CompatibilityScreen screen,ITypedIngredient<I> ingredient,boolean start){Object value=ingredient.getIngredient();var out=new ArrayList<Target<I>>();for(var target:screen.dropTargets(value))out.add(new Target<>(){public Rect2i getArea(){return target.area();}public void accept(I value){target.accept().accept(value);}});return out;}
            public void onComplete(){}
        };}
    public static IGhostIngredientHandler<FieldPatchScreen> patchGhostHandler(){return new IGhostIngredientHandler<FieldPatchScreen>(){public <I> List<Target<I>> getTargetsTyped(FieldPatchScreen screen,ITypedIngredient<I> ingredient,boolean start){var result=new ArrayList<Target<I>>();for(var target:screen.ghostTargets(ingredient.getIngredient()))result.add(new Target<>(){public Rect2i getArea(){return target.area();}public void accept(I value){target.accept().accept(value);}});return result;}public void onComplete(){}};}
    @Override public void registerAdvanced(IAdvancedRegistration registration){
        JeiRecipeButtons.register(registration);
    }
}
