package dev.kubejseditor.verification;

import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Dev-only native rendering, hit testing and resource reload scenarios. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientFanVerification {
    private static int phase,ticks,checks,shotDelay,gallery;
    private static String shot;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static final List<String> TYPES=new ArrayList<>();
    static {TYPES.addAll(List.of("minecraft:crafting_shaped","minecraft:smelting","create:mixing","create:compacting","create:pressing","create:filling"));FanProcessing.all().forEach(m->TYPES.add(m.type()));}
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object get(String key)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);return f.get(screen);}
    private static void set(String key,Object value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(key);f.setAccessible(true);f.set(screen,value);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static void frame()throws Exception{call("rebuildWidgets",new Class[]{});}
    private static void capture(String name){org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(),1,1);shot=name;shotDelay=8;}
    private static EditorProject.RecipeDraft current()throws Exception{return (EditorProject.RecipeDraft)get("recipe");}
    private static void select(String type)throws Exception{var d=new EditorProject.RecipeDraft("kubejs_editor:v6_view",RecipeAdapters.get(type).newRecipe());call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},d,false);}
    private static void verifyBounds()throws Exception{
        check(!screen.ghostTargets().isEmpty(),"Visible ghost slots");
        for(var t:screen.ghostTargets()){var a=t.area();check(a.getX()>=(int)get("canvasX")&&a.getX()+a.getWidth()<=(int)get("propX")&&a.getY()>=(int)call("canvasTop",new Class[]{})&&a.getY()+a.getHeight()<=(int)call("canvasBottom",new Class[]{}),"Ghost slot clipped to canvas");}
        check(CreateSceneRenderer.failures().isEmpty(),"Native device, rotor and catalyst render without reflection errors");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyFan")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        if(shot!=null&&--shotDelay<=0){Screenshot.grab(mc.gameDirectory,"kjs-v060-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(net.neoforged.fml.ModList.get().getModContainerById("kubejs_editor").orElseThrow().getModInfo().getVersion().toString().equals("0.11.0"),"New release loaded");
                for(String mod:List.of("jei","create","create_dragons_plus","aeronautics","sable","ftblibrary","inventoryprofilesnext"))check(net.neoforged.fml.ModList.get().isLoaded(mod),"Compatibility mod: "+mod);
                for(var mode:FanProcessing.all()){EditorRuntime.validateRecipe(RecipeAdapters.get(mode.type()).newRecipe(),Set.of());checks++;}
                for(String type:List.of("create:mixing","create:compacting"))for(String heat:List.of("none","heated","superheated")){var data=RecipeAdapters.get(type).newRecipe();data.addProperty("heat_requirement",heat);EditorRuntime.validateRecipe(data,Set.of());checks++;}
                var review=EditorRuntime.review();if(!review.changes().isEmpty())EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timed out phase "+phase);
            switch(phase){
                case 2 -> {if(result==null)return;check(result.success(),"All 26 serializer fixtures load through KJS");for(var mode:FanProcessing.all())check(EditorRuntime.recipes().stream().anyMatch(r->r.type().equals(mode.type())&&r.error()==null),"Fan recipe imports without loss: "+mode.type());screen=new WorkspaceScreen();mc.setScreen(screen);select(TYPES.get(0));capture("gallery-0");}
                case 3 -> {
                    verifyBounds();var fit=(CanvasFit)get("canvasFit");check(fit.scale()>=.9,"Recipes are readable at GUI scale 2: "+TYPES.get(gallery));
                    if(gallery==0)check(fit.scale()>1,"Workbench enlarges");
                    if(++gallery<TYPES.size()){select(TYPES.get(gallery));capture("gallery-"+gallery);ticks=0;return;}
                    mc.options.guiScale().set(3);mc.resizeDisplay();gallery=0;select("create:mixing");capture("mixer-scale3");
                }
                case 4 -> {verifyBounds();check(((CanvasFit)get("canvasFit")).scale()>.8,"Compact mixer no longer shrinks to 43 percent");select("create:splashing");capture("water-scale3");}
                case 5 -> {verifyBounds();mc.options.guiScale().set(2);mc.resizeDisplay();select("create:sequenced_assembly");for(int i=0;i<9;i++)RecipeCanvas.addStep(current().data,i%2==0?"create:deploying":"create:pressing");frame();capture("sequence-start");}
                case 6 -> {
                    verifyBounds();check(Math.abs(((CanvasFit)get("canvasFit")).scale()-SequenceLayout.scale((int)get("canvasW")-12))<.0001,"Long sequence retains scale independent of step count");check((int)get("sequenceScrollMax")>1000,"Long sequence scroll range");
                    var first=screen.ghostTargets().stream().filter(t->!t.fluid()).findFirst().orElseThrow();first.accept().accept(new ItemStack(Items.GOLD_INGOT));
                    int x=(int)call("canvasRight",new Class[]{})-2,y=(int)get("bodyY")+22;screen.mouseClicked(x,y,0);screen.mouseDragged(x,y,0,20,0);screen.mouseReleased(x,y,0);capture("sequence-end");
                }
                case 7 -> {
                    verifyBounds();check((int)get("canvasScrollX")>=(int)get("sequenceScrollMax")-2,"Top scrollbar reaches final card");
                    var slot=((List<RecipeCanvas.Slot>)get("slots")).stream().filter(s->s.label().equals("步骤 10")).findFirst().orElseThrow();var bounds=(net.minecraft.client.renderer.Rect2i)call("visibleSlotBounds",new Class[]{RecipeCanvas.Slot.class},slot);check(bounds!=null,"Final step material slot visible");
                    var target=screen.ghostTargets().stream().filter(t->t.area().getX()==bounds.getX()&&t.area().getY()==bounds.getY()).findFirst().orElseThrow();target.accept().accept(new ItemStack(Items.DIAMOND));check(current().data.getAsJsonArray("sequence").get(9).getAsJsonObject().getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:diamond"),"Scrolled JEI drop reaches precise last step");
                    select("create:mechanical_crafting");CraftingGrid.resize(current(),9);frame();capture("mechanical9");
                }
                case 8 -> {verifyBounds();check(screen.ghostTargets().size()==82,"9x9 still fits every target");mc.options.guiScale().set(3);mc.resizeDisplay();frame();capture("mechanical9-scale3");}
                case 9 -> {verifyBounds();check(screen.ghostTargets().size()==82,"9x9 fits at GUI scale 3");check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI remains visible");select("create:sequenced_assembly");for(int i=0;i<9;i++)RecipeCanvas.addStep(current().data,"create:deploying");frame();screen.mouseScrolled((int)get("canvasX")+20,(int)get("bodyY")+60,0,-25);capture("sequence-summary-scale3");}
                case 10 -> {verifyBounds();check(Math.abs(((CanvasFit)get("canvasFit")).scale()-SequenceLayout.scale((int)get("canvasW")-12))<.0001,"Compact sequence displays about 2.5 cards");var summary=((List<RecipeCanvas.Slot>)get("slots")).stream().filter(s->s.label().equals("最终产物 1")).findFirst().orElseThrow();check(call("visibleSlotBounds",new Class[]{RecipeCanvas.Slot.class},summary)!=null,"Scrolling both axes reveals sequence final products at scale 3");Files.writeString(root().resolve("fan-result.json"),"{\"passed\":true,\"checks\":"+checks+",\"serializers\":26}");mc.stop();phase=99;return;}
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("fan-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
