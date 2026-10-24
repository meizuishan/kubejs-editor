package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.config.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Actual Create scenes, ghost slots, native codecs and resource reload in a disposable world. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientCanvasVerification {
    private static int phase,ticks,checks,shotTicks,configCount;private static String shot;
    private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static final List<String> SCENES=List.of("deploying","pressing","compacting","mixing","cutting","crushing","milling","filling","emptying","splashing","haunting","sandpaper_polishing","item_application");
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Object get(String field)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(field);f.setAccessible(true);return f.get(screen);}
    private static void set(String field,Object value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(field);f.setAccessible(true);f.set(screen,value);}
    private static void call(String name)throws Exception{for(Class<?> c=WorkspaceScreen.class;c!=null;c=c.getSuperclass())try{var m=c.getDeclaredMethod(name);m.setAccessible(true);m.invoke(screen);return;}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(name);}
    private static void select(EditorProject.RecipeDraft draft)throws Exception{
        screen=new WorkspaceScreen();Minecraft.getInstance().setScreen(screen);set("tab",draft.data.get("type").getAsString().startsWith("create:")?2:1);
        var m=WorkspaceScreen.class.getDeclaredMethod("selectRecipe",EditorProject.RecipeDraft.class,boolean.class);m.setAccessible(true);m.invoke(screen,draft,false);
    }
    private static EditorProject.RecipeDraft current()throws Exception{return (EditorProject.RecipeDraft)get("recipe");}
    private static void click(String text){var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+text));check(b.active,"Button active: "+text);screen.mouseClicked(b.getX()+3,b.getY()+3,0);}
    private static void capture(String name){shot=name;shotTicks=8;}
    private static EditorProject.RecipeDraft mechanical(){var m=new EditorProject.RecipeDraft("kubejs_editor:canvas_mechanical",RecipeAdapters.get("create:mechanical_crafting").newRecipe());CraftingGrid.resize(m,9);for(int i:new int[]{10,12,28,30})RecipeCanvas.slots(m).get(i).set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));return m;}
    @SuppressWarnings("unchecked") private static boolean crafts(String id,Item item){var mc=Minecraft.getInstance();var r=mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id));return r.isPresent()&&((Recipe<CraftingInput>)r.get().value()).matches(CraftingInput.of(1,1,List.of(new ItemStack(item))),mc.level);}
    private static void apply()throws Exception{var review=EditorRuntime.review();check(!review.restartRequired()&&!review.resourcesChanged(),"Recipe edits do not reload startup scripts or resources");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyCanvas")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;
        if(shot!=null&&--shotTicks<=0){Screenshot.grab(mc.gameDirectory,"abyss-v040-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.player!=null&&mc.level!=null&&ticks>120){
                check(net.neoforged.fml.ModList.get().getModContainerById("kubejs_editor").orElseThrow().getModInfo().getVersion().toString().equals("0.11.0"),"New release metadata");
                for(String mod:List.of("jei","create","aeronautics","sable","ftblibrary","inventoryprofilesnext"))check(net.neoforged.fml.ModList.get().isLoaded(mod),"Compatibility mod: "+mod);
                check(crafts("minecraft:oak_button",Items.OAK_PLANKS),"Original recipe loaded");apply();phase=2;ticks=0;return;
            }
            if(phase<2||ticks<25)return;if(ticks>1800)throw new AssertionError("Timeout at phase "+phase+": "+EditorRuntime.activationError);
            if(phase==2){
                if(result==null)return;check(result.success(),"Fixture activation: "+result);
                var r=EditorRuntime.recipes().stream().filter(v->v.id().equals("minecraft:oak_button")).findFirst().orElseThrow();var d=new EditorProject.RecipeDraft(r.id(),r.data());d.operation="replace";d.origin="existing";select(d);
                check(RecipeCanvas.slots(current()).size()==10&&current().grid.size==3,"Native 1x1 recipe expands to fixed 3x3");
                check(get("form").toString().contains("合成方式")&&!get("form").toString().contains("启用规则"),"Order control replaces enabled flag");if(current().data.get("type").getAsString().endsWith("shapeless"))click("无序");capture("crafting");
            }else if(phase==3){
                check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI visible on plain Screen");
                check(screen.ghostTargets().size()==10,"JEI exposes exactly nine inputs and output");
                var targets=screen.ghostTargets();var first=targets.getFirst();screen.mouseClicked(first.area().getX()+3,first.area().getY()+3,1);
                var inventory=mc.player.getInventory().getItem(0).copy();targets.get(4).accept().accept(new ItemStack(Items.IRON_INGOT));
                check(ItemStack.matches(inventory,mc.player.getInventory().getItem(0)),"Ghost drop does not consume inventory");
                var cells=current().grid.cells.deepCopy();click("有序");check(current().data.get("type").getAsString().endsWith("shapeless")&&current().grid.cells.equals(cells),"UI order toggle retains position and item");
                call("stage");capture("shapeless");
            }else if(phase==4){click("一键应用");}
            else if(phase==5){
                if(EditorRuntime.busy()||EditorRuntime.state().activation!=null)return;
                check(EditorRuntime.activationError.isBlank()&&EditorRuntime.project().recipes.isEmpty(),"One-click application clears successful pending work");
                check(crafts("minecraft:oak_button",Items.IRON_INGOT)&&!crafts("minecraft:oak_button",Items.OAK_PLANKS),"Actual recipe follows unordered edit");
                WorkspaceScreen.openRecipe("minecraft:oak_button",null);screen=(WorkspaceScreen)mc.screen;
                check(current().grid.cells.get(4).getAsJsonObject().get("item").getAsString().equals("minecraft:iron_ingot"),"JEI reopening retains visual position after apply");
                result=null;EditorRuntime.revokeRecipe("minecraft:oak_button",r->result=r);
            }else if(phase==6){if(result==null)return;check(result.success()&&crafts("minecraft:oak_button",Items.OAK_PLANKS),"Revoke still restores original recipe");select(mechanical());capture("mechanical-9x9");}
            else if(phase==7){
                check(current().grid.size==9&&RecipeCanvas.slots(current()).size()==82,"Native mechanical grid offers 9x9");
                var d=current();EditorRuntime.project().recipes.put(d.id,ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(d),EditorProject.RecipeDraft.class));
                var full=mechanical();full.id="kubejs_editor:canvas_full9";for(var cell:RecipeCanvas.slots(full))if(!cell.output())cell.set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));EditorRuntime.project().recipes.put(full.id,full);
                EditorRuntime.saveDraft();check(EditorRuntime.validationIssues().isEmpty(),"Native Create codec accepts cropped 9x9 editor data");apply();
            }else if(phase==8){
                if(result==null)return;check(result.success(),"Mechanical crafting reload: "+result);
                var loaded=EditorRuntime.recipes().stream().filter(r->r.id().equals("kubejs_editor:canvas_mechanical")).findFirst().orElseThrow();check(loaded.data().getAsJsonArray("pattern").toString().equals("[\"A A\",\"   \",\"A A\"]"),"Loaded mechanical recipe trims outer border and keeps internal holes");
                var full=EditorRuntime.recipes().stream().filter(r->r.id().equals("kubejs_editor:canvas_full9")).findFirst().orElseThrow().data().getAsJsonArray("pattern");check(full.size()==9&&full.get(0).getAsString().length()==9,"Full 81-input native Create recipe loads at 9x9");
                WorkspaceScreen.openRecipe(loaded.id(),null);screen=(WorkspaceScreen)mc.screen;check(current().grid.size==9,"Persisted editor size survives native cropped recipe");
            }else if(phase>=9&&phase<9+SCENES.size()){
                String type="create:"+SCENES.get(phase-9);select(new EditorProject.RecipeDraft("kubejs_editor:scene_"+(phase-9),RecipeAdapters.get(type).newRecipe()));
                check(get("slots") instanceof List<?> l&&!l.isEmpty(),"Role slots: "+type);capture(SCENES.get(phase-9));
            }else if(phase==22){
                var blank=RecipeAdapters.get("create:filling").newRecipe();blank.getAsJsonArray("ingredients").set(1,RecipeAdapters.parse("{amount:250}"));select(new EditorProject.RecipeDraft("kubejs_editor:blank_spout",blank));capture("empty-spout");
            }else if(phase==23){
                var blank=RecipeAdapters.get("create:emptying").newRecipe();blank.getAsJsonArray("results").set(1,RecipeAdapters.parse("{amount:250}"));select(new EditorProject.RecipeDraft("kubejs_editor:blank_drain",blank));capture("empty-drain");
            }else if(phase==24){
                check(CreateSceneRenderer.failures().isEmpty(),"All native Create scenes render: "+CreateSceneRenderer.failures());
                select(new EditorProject.RecipeDraft("kubejs_editor:empty_cutting",RecipeAdapters.get("create:cutting").newRecipe()));click("+ 产物");
                check(current().data.getAsJsonArray("results").get(1).isJsonNull(),"Adding output creates empty editable slot");
                click("+ 产物");click("+ 产物");var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals("+ 产物")).map(c->(Button)c).findFirst().orElseThrow();check(!b.active,"Cutting output stops at four slots");
                call("deleteRecipe");
                var configs=CreateConfigs.list();configCount=configs.size();List<String> names=new ArrayList<>();
                for(var c:configs){String name=ConfigLabels.name(c);check(name.codePoints().anyMatch(n->n>=0x3400&&n<=0x9fff),"Chinese config name: "+c.key()+" -> "+name);names.add(c.key()+" = "+name);}
                Files.write(root().resolve("canvas-config-labels.txt"),names);
                screen=new WorkspaceScreen();mc.setScreen(screen);set("tab",2);set("createTab",1);
                var stress=configs.stream().filter(c->c.path().contains("impact")&&c.path().getLast().equals("mechanical_mixer")).findFirst().orElseThrow();set("selectedConfig",stress);set("configText",stress.text(stress.current()));call("rebuildWidgets");
                check(ConfigLabels.name(stress).contains("应力消耗")&&!ConfigLabels.name(stress).contains("mechanical_mixer"),"Stress settings use machine Chinese name");capture("config-chinese");
            }else if(phase==25){
                call("browseConfigs");check(mc.screen instanceof ChoiceScreen,"Chinese parameter selector opens");capture("config-list");
            }else if(phase==26){
                check(CreateSceneRenderer.failures().isEmpty(),"Rendering stays healthy");mc.setScreen(null);mc.options.guiScale().set(3);mc.resizeDisplay();select(mechanical());capture("mechanical-scale3");
            }else if(phase==27){
                check(screen.guiBounds().getWidth()>0&&screen.ghostTargets().stream().noneMatch(g->g.area().getX()+g.area().getWidth()>((int)getUnchecked("propX"))),"Scaled canvas clips ghost targets inside canvas");
                check((int)get("listW")>0&&screen.ghostTargets().size()==82,"Sidebar remains visible while full grid fits");
                var d=current();EditorRuntime.project().recipes.put(d.id,ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(d),EditorProject.RecipeDraft.class));call("rebuildWidgets");var cancel=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("×")&&b.getWidth()==18).map(c->(Button)c).findFirst().orElseThrow();cancel.onPress();check(EditorRuntime.project().recipes.isEmpty(),"Visible sidebar cancels exactly one pending recipe");
                set("canvasScrollX",144);var open=WorkspaceScreen.class.getDeclaredMethod("selectLoaded",EditorRuntime.Loaded.class);open.setAccessible(true);open.invoke(screen,EditorRuntime.recipes().stream().filter(r->r.id().equals("minecraft:oak_button")).findFirst().orElseThrow());check((int)get("canvasScrollX")==0,"Opening another recipe resets horizontal scroll");
                set("canvasScrollX",144);set("tab",0);call("newRecipe");var create=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("工作台 · 有序")).map(c->(Button)c).findFirst().orElseThrow();create.onPress();check((int)get("canvasScrollX")==0&&current().grid.size==3,"New crafting recipe resets scroll and keeps fixed 3x3");call("deleteRecipe");
                Files.writeString(root().resolve("canvas-verification.json"),"{\"passed\":true,\"checks\":"+checks+",\"config_entries\":"+configCount+"}");mc.stop();phase=99;return;
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("canvas-verification.json"),"{\"passed\":false,\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
    private static Object getUnchecked(String field){try{return get(field);}catch(Exception e){throw new IllegalStateException(e);}}
}
