package dev.kubejseditor.verification;

import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Native tests of direct disable actions and the 2.5-card sequence viewport. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientQuickVerification {
    private static int phase,ticks,checks,shotDelay;
    private static String shot;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static final String OAK="minecraft:oak_button",KEEP="kubejs_editor:v61_keep";
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object get(String key)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);return f.get(screen);}
    private static void set(String key,Object value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);f.set(screen,value);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static void frame()throws Exception{call("rebuildWidgets",new Class[]{});}
    private static void capture(String name){shot=name;shotDelay=8;}
    private static EditorProject.RecipeDraft current()throws Exception{return (EditorProject.RecipeDraft)get("recipe");}
    private static EditorRuntime.Loaded loaded(String id){return EditorRuntime.recipes().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow();}
    private static Button button(String label){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button: "+label));}
    private static void click(String label){var b=button(label);check(b.active,"Active button: "+label);Minecraft.getInstance().screen.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);}
    private static void search(String text){Minecraft.getInstance().screen.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow().setValue(text);}
    private static boolean exists(String id){return Minecraft.getInstance().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static void cancel(String id)throws Exception{call("cancelOperation",new Class[]{PendingOperation.class},new PendingOperation("recipe",id,"配方禁用"));}
    @SuppressWarnings("unchecked") private static void verifySequence()throws Exception{
        var fit=(CanvasFit)get("canvasFit");double right=fit.localX((int)call("canvasRight",new Class[]{}));
        check(Math.abs((right-SequenceLayout.cardX(current().data,2))/SequenceLayout.CARD_WIDTH-.5)<.03,"Exactly two cards and half the third are visible");
        check(fit.scale()<=1,"Cards remain smaller than v0.6.0");
        check(!screen.ghostTargets().isEmpty(),"Visible sequence drag targets");
        for(var target:screen.ghostTargets()){var a=target.area();check(a.getX()>=(int)call("canvasLeft",new Class[]{})&&a.getX()+a.getWidth()<=(int)call("canvasRight",new Class[]{})&&a.getY()>=(int)call("canvasTop",new Class[]{})&&a.getY()+a.getHeight()<=(int)call("canvasBottom",new Class[]{}),"Drag targets stay within the reduced viewport");}
        check(CreateSceneRenderer.failures().isEmpty(),"Native step animations render");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyQuick")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        if(shot!=null&&--shotDelay<=0){Screenshot.grab(mc.gameDirectory,"kjs-v061-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(net.neoforged.fml.ModList.get().getModContainerById("kubejs_editor").orElseThrow().getModInfo().getVersion().toString().equals("0.11.0"),"New release loaded");
                var review=EditorRuntime.review();if(!review.changes().isEmpty())EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timed out phase "+phase);
            switch(phase){
                case 2 -> {if(result==null)return;check(result.success(),"Fixture activation");screen=new WorkspaceScreen();mc.setScreen(screen);call("selectLoaded",new Class[]{EditorRuntime.Loaded.class},loaded(OAK));capture("direct-disable");}
                case 3 -> {click("禁用配方");check(EditorRuntime.project().recipes.get(OAK).operation.equals("remove")&&get("recipe")==null,"Direct disable queues one operation and clears active edit");check(exists(OAK),"Disable remains pending until apply");cancel(OAK);check(!EditorRuntime.project().recipes.containsKey(OAK),"Cancel restores loaded recipe without another operation");var keep=new EditorProject.RecipeDraft(KEEP,RecipeAdapters.get("create:pressing").newRecipe());EditorRuntime.project().recipes.put(KEEP,keep);EditorRuntime.saveDraft();call("browseRecipes",new Class[]{boolean.class},false);search(OAK);capture("recipe-list");}
                case 4 -> {click("禁用配方");check(mc.screen instanceof ChoiceScreen&&!button("禁用配方").active,"List remains open and disables repeated submission");check(EditorRuntime.project().recipes.get(OAK).operation.equals("remove")&&EditorRuntime.project().recipes.containsKey(KEEP),"List disables precise ID and retains unrelated operation");check(ProjectStore.read(root()).recipes.get(OAK).operation.equals("remove"),"Direct list disable persists across reopening");capture("list-disabled");}
                case 5 -> {mc.screen.onClose();cancel(OAK);check(EditorRuntime.project().recipes.containsKey(KEEP),"Cancel affects only selected disable");var opaque=EditorRuntime.recipes().stream().filter(r->r.error()!=null).findFirst().orElseThrow();call("browseRecipes",new Class[]{boolean.class},false);search(opaque.id());click("禁用配方");check(EditorRuntime.project().recipes.get(opaque.id()).operation.equals("remove"),"Unsupported real recipe can also be disabled by exact ID");mc.screen.onClose();cancel(opaque.id());cancel(KEEP);call("selectLoaded",new Class[]{EditorRuntime.Loaded.class},loaded(OAK));click("禁用配方");call("applyAll",new Class[]{});}
                case 6 -> {if(EditorRuntime.busy()||EditorRuntime.state().activation!=null)return;check(!exists(OAK)&&EditorRuntime.state().applied.recipes.get(OAK).operation.equals("remove"),"Applying direct disable actually removes native recipe");check(EditorRuntime.project().recipes.isEmpty(),"Successful apply clears pending operation");call("revokeAppliedRecipe",new Class[]{String.class},OAK);button("确认撤销").onPress();}
                case 7 -> {if(EditorRuntime.busy()||EditorRuntime.state().activation!=null)return;check(exists(OAK)&&!EditorRuntime.state().applied.recipes.containsKey(OAK)&&EditorRuntime.project().recipes.isEmpty(),"Revoke restores original recipe without generating pending operations");var d=new EditorProject.RecipeDraft("kubejs_editor:v61_sequence",RecipeAdapters.get("create:sequenced_assembly").newRecipe());RecipeCanvas.addStep(d.data,"create:deploying");RecipeCanvas.addStep(d.data,"create:pressing");call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},d,false);capture("sequence-three");}
                case 8 -> {verifySequence();double before=((CanvasFit)get("canvasFit")).scale();for(int i=0;i<9;i++)RecipeCanvas.addStep(current().data,"create:deploying");frame();set("status","Sequence scale before: "+before);capture("sequence-twelve");}
                case 9 -> {verifySequence();check(Math.abs(((CanvasFit)get("canvasFit")).scale()-SequenceLayout.scale((int)get("canvasW")-12))<.0001,"Length does not change scale");int x=(int)call("canvasRight",new Class[]{})-2,y=(int)get("bodyY")+22;screen.mouseClicked(x,y,0);screen.mouseDragged(x,y,0,10,0);screen.mouseReleased(x,y,0);capture("sequence-last");}
                case 10 -> {check((int)get("canvasScrollX")>=(int)get("sequenceScrollMax")-2,"Scrollbar reaches final card");@SuppressWarnings("unchecked") var slots=(List<RecipeCanvas.Slot>)get("slots");var slot=slots.stream().filter(s->s.label().equals("步骤 12")).findFirst().orElseThrow();var a=(net.minecraft.client.renderer.Rect2i)call("visibleSlotBounds",new Class[]{RecipeCanvas.Slot.class},slot);check(a!=null,"Last step material remains visible");screen.ghostTargets().stream().filter(t->t.area().getX()==a.getX()&&t.area().getY()==a.getY()).findFirst().orElseThrow().accept().accept(new ItemStack(Items.DIAMOND));check(current().data.getAsJsonArray("sequence").get(11).getAsJsonObject().getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:diamond"),"Smaller scrolled target edits exact final step");mc.options.guiScale().set(3);mc.resizeDisplay();set("canvasScrollX",0);set("canvasScroll",0);frame();capture("sequence-scale3");}
                case 11 -> {verifySequence();check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI remains displayed");Files.writeString(root().resolve("quick-result.json"),"{\"passed\":true,\"checks\":"+checks+"}");mc.stop();phase=99;return;}
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("quick-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
