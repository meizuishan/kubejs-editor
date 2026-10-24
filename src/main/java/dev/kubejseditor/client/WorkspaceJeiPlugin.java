package dev.kubejseditor.client;

import mezz.jei.api.*;
import mezz.jei.api.gui.handlers.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IAdvancedRegistration;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.*;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

/** Adapted from Astronomy20's MIT-licensed RecipeBuilderGhostHandler. */
@JeiPlugin
public final class WorkspaceJeiPlugin implements IModPlugin {
    public static mezz.jei.api.runtime.IJeiRuntime runtime;
    @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime value){runtime=value;dev.kubejseditor.runtime.EditorRuntime.invalidateRecipes();}
    @Override public void onRuntimeUnavailable(){runtime=null;dev.kubejseditor.recipe.SimpleRecipes.clearJeiEvidence();dev.kubejseditor.runtime.EditorRuntime.invalidateRecipes();}
    @Override public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("kubejs_editor","workspace");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
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
    @Override public void registerAdvanced(IAdvancedRegistration registration){
        var raw=registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(new ItemStack(Items.WRITABLE_BOOK));
        var icon=new mezz.jei.api.gui.drawable.IDrawable(){public int getWidth(){return 10;}public int getHeight(){return 10;}public void draw(net.minecraft.client.gui.GuiGraphics g,int x,int y){g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(0.625f,0.625f,1);raw.draw(g,0,0);g.pose().popPose();}};
        registration.addRecipeButtonFactory(new mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory(){
            @Override public <T> IIconButtonController createButtonController(IRecipeLayoutDrawable<T> layout){
                var mc=Minecraft.getInstance();if(mc.level==null)return null;
                var source=JeiRecipeSources.resolve(layout);ResourceLocation id=source.id();
                if(id!=null)JeiRecipeSources.capture(layout,id);
                return new IIconButtonController(){
                    @Override public void initState(IButtonState state){state.setIcon(icon);state.setVisible(true);state.setActive(true);}
                    @Override public void getTooltips(ITooltipBuilder tooltip){tooltip.add(Component.literal(id==null?"查看配方适配信息":"使用 KubeJS Editor 编辑此配方"));tooltip.add(Component.literal(id==null?source.reason():id.toString()));}
                    @Override public boolean onPress(IJeiUserInput input){if(!input.isSimulate()){var previous=mc.screen;mc.tell(()->{
                        try{if(id==null)mc.setScreen(new TextScreen(previous,"JEI 配方来源",JeiRecipeSources.diagnostics(layout,source.reason())));else{dev.kubejseditor.runtime.EditorRuntime.invalidateRecipes();WorkspaceScreen.openRecipe(id.toString(),previous);}}catch(Exception e){if(mc.player!=null)mc.player.sendSystemMessage(Component.literal("无法打开配方："+e.getMessage()));}
                    });}return true;}
                };
            }
        });
    }
}
