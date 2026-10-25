package dev.kubejseditor.verification;

import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientCompatibilityVerification {
    private static int phase,ticks,checks,valid,recipeCount;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static final String PROFILE=System.getProperty("kubejs.editor.compatProfile","full");
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name+" in "+object.getClass().getName());}
    private static void finish()throws Exception{
        var mods=net.neoforged.fml.ModList.get();String jei=mods.getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString();
        Files.writeString(root().resolve("compatibility-result.json"),"{\"passed\":true,\"profile\":\""+PROFILE+"\",\"jei\":"+ProjectStore.JSON.toJson(jei)+",\"neoforge\":"+ProjectStore.JSON.toJson(mods.getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString())+",\"checks\":"+checks+",\"validRecipes\":"+valid+",\"jeiSideButtonAndDrag\":true}");Minecraft.getInstance().stop();phase=99;
    }
    private static Button button(String text){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow();}
    private static void click(String text){var b=button(text);check(b.active,"Active: "+text);b.onPress();}
    @SuppressWarnings("unchecked") private static List<ChoiceScreen.Choice> choices()throws Exception{var f=ChoiceScreen.class.getDeclaredField("choices");f.setAccessible(true);return (List<ChoiceScreen.Choice>)f.get(Minecraft.getInstance().screen);}
    private static void capture(String name){Screenshot.grab(Minecraft.getInstance().gameDirectory,"compat-"+PROFILE+"-"+name+".png",Minecraft.getInstance().getMainRenderTarget(),c->{});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyCompatibility")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(ModList.get().isLoaded("create")==!PROFILE.equals("core"),"Actual Create classpath matches profile");
                check(ModList.get().isLoaded("create_dragons_plus")==Set.of("dragons","full").contains(PROFILE),"Actual addon classpath matches profile");
                check(ModList.get().isLoaded("kubejs_create")==PROFILE.equals("full"),"KubeJS Create is optional");
                check(EditorRuntime.project().schemaVersion==2&&EditorRuntime.project().recipes.size()==3,"Legacy draft migrated without dropping optional content");
                check(Files.readString(root().resolve("kubejs/config/kubejs_editor/project.v1.backup.json")).contains('"'+"schemaVersion"+'"'),"Legacy source backed up");
                screen=new WorkspaceScreen();mc.setScreen(screen);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timeout phase "+phase);
            switch(phase){
                case 2 -> {
                    check(button("机械动力").active==EditorCompatibility.hasCreate(),"Missing Create disables navigation with an explanation");capture("workspace");click("+配方");
                    check(choices().stream().anyMatch(c->c.text().contains("工作台")),"Vanilla creation remains available");
                    check(choices().stream().anyMatch(c->c.text().equals("机械动力"))==EditorCompatibility.hasCreate(),"Create submenu only appears when available");check(choices().stream().noneMatch(c->c.text().equals("鼓风机加工")),"Fan is nested inside Create");mc.screen.onClose();
                    var issues=EditorRuntime.validationIssues();int missing=0;for(var d:new ArrayList<>(EditorRuntime.project().recipes.values())){
                        if(!EditorCompatibility.hasRecipeType(d.data.get("type").getAsString())){
                            missing++;check(issues.stream().anyMatch(i->i.key().equals("recipe:"+d.id)&&i.message().contains("缺少")),"Missing dependency error identifies its operation");
                            call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},d,false);check(button("兼容信息").active,"Missing draft exposes compatibility details");
                            EditorRuntime.cancel(new PendingOperation("recipe",d.id,"配方生成"));
                        }else valid++;
                    }
                    check(missing==(PROFILE.equals("core")?2:PROFILE.equals("create")?1:0),"Only unavailable adapters fail");
                    call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},EditorRuntime.project().recipes.get("compat:legacy"),false);
                }
                case 3 -> {check(screen.ghostTargets().size()==10,"Legacy crafting presents a full 3x3 canvas");mc.options.guiScale().set(3);mc.resizeDisplay();}
                case 4 -> {check(screen.ghostTargets().size()==10&&WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"Vanilla ghost slots and JEI work after resizing");capture("scale3");check(EditorRuntime.validationIssues().isEmpty(),"Remaining draft validates with available mods only");var review=EditorRuntime.review();check(!review.restartRequired(),"Recipe-only activation does not require restart");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 5 -> {if(result==null)return;check(result.success(),"Application and native reload succeed");check(EditorRuntime.project().recipes.isEmpty()&&EditorRuntime.state().applied.recipes.size()==valid,"Only submitted valid operations promoted");for(var d:EditorRuntime.state().applied.recipes.values())check(mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(d.id)).isPresent(),"Actual native recipe: "+d.id);recipeCount=mc.getSingleplayerServer().getRecipeManager().getRecipes().size();result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 6 -> {if(result==null)return;check(result.success()&&recipeCount==mc.getSingleplayerServer().getRecipeManager().getRecipes().size(),"Repeated reload preserves recipe count");String jei=net.neoforged.fml.ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString();check(EditorCompatibility.report().contains("jei："+jei),"Compatibility report contains real versions");check(CreateSceneRenderer.failures().isEmpty(),"Optional scenes do not fail the workspace");var runtime=WorkspaceJeiPlugin.runtime;var category=runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);var recipe=mc.level.getRecipeManager().byKey(ResourceLocation.parse("compat:legacy")).orElseThrow();@SuppressWarnings("unchecked") var crafting=(net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>)(Object)recipe;runtime.getRecipesGui().showRecipes(category,List.of(crafting),List.of());}
                case 7 -> {var layouts=field(mc.screen,"layouts");var list=(List<?>)field(layouts,"recipeLayoutsWithButtons");var buttons=(List<?>)field(list.getFirst(),"buttons");mezz.jei.api.gui.buttons.IIconButtonController edit=null;for(Object b:buttons){var controller=(mezz.jei.api.gui.buttons.IIconButtonController)field(b,"controller");if(dev.kubejseditor.client.JeiRecipeButtons.isEditorController(controller))edit=controller;}check(edit!=null,"JEI attaches editor button to actual recipe layout");edit.onPress(new mezz.jei.api.gui.inputs.IJeiUserInput(){public com.mojang.blaze3d.platform.InputConstants.Key getKey(){return com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);}public int getModifiers(){return 0;}public boolean isSimulate(){return false;}public boolean is(net.minecraft.client.KeyMapping key){return false;}public boolean is(mezz.jei.api.runtime.IJeiKeyMapping key){return false;}});}
                case 8 -> {check(mc.screen instanceof WorkspaceScreen,"JEI side button opens workspace");screen=(WorkspaceScreen)mc.screen;var draft=(EditorProject.RecipeDraft)field(screen,"recipe");check(draft.id.equals("compat:legacy"),"Side button opens exact recipe ID");check(screen.ghostTargets().size()==10,"JEI ghost handler exposes all crafting slots");screen.ghostTargets().getFirst().accept().accept(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));draft=(EditorProject.RecipeDraft)field(screen,"recipe");check(draft.grid.cells.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:diamond"),"Ghost drag edits the targeted ingredient");finish();return;}
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("compatibility-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
