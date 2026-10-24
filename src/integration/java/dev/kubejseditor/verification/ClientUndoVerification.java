package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.RecipeAdapters;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Focused rule-revocation scenarios in an isolated client, including the exact oak-button example. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientUndoVerification {
    private static int phase,ticks,checks,shotTicks;private static String shot;
    private static EditorRuntime.ReloadResult result;
    private static final String OAK="minecraft:oak_button",NEW="kubejs_editor:undo_new",FAIL="kubejs_editor:undo_failure",LAST="kubejs_editor:undo_last";
    private static final String ERROR="kubejs/server_scripts/undo_expected_error.js";
    private static WorkspaceScreen screen;private static JsonObject baseline;private static String pendingText;
    private static byte[] startup,config;private static Path configFile;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    private static void ready(){check(result!=null&&result.success(),"Reload succeeded: "+result);}
    private static void apply(EditorProject.RecipeDraft d)throws Exception{EditorRuntime.project().recipes.put(d.id,d);EditorRuntime.saveDraft();var review=EditorRuntime.review();check(!review.restartRequired(),"Recipe-only transaction remains hot-reloadable");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);}
    private static EditorProject.RecipeDraft recipe(String id,String input,String operation){var d=new EditorProject.RecipeDraft(id,RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'"+input+"'}],result:{id:'minecraft:oak_button'}}"));d.operation=operation;d.origin=id.equals(OAK)?"existing":"add";return d;}
    private static boolean exists(String id){return Minecraft.getInstance().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    @SuppressWarnings("unchecked") private static boolean crafts(String id,Item item){var mc=Minecraft.getInstance();var found=mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id));return found.isPresent()&&((Recipe<CraftingInput>)found.get().value()).matches(CraftingInput.of(1,1,List.of(new ItemStack(item))),mc.level);}
    private static void undo(String id)throws Exception{result=null;EditorRuntime.revokeRecipe(id,r->result=r);}
    private static void capture(String name){shot=name;shotTicks=8;}
    private static void existing(String filter)throws Exception{screen=new WorkspaceScreen();Minecraft.getInstance().setScreen(screen);set("tab",4);var f=WorkspaceScreen.class.getDeclaredField("filter");f.setAccessible(true);f.set(screen,filter);refresh();}
    private static void set(String name,int value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(name);f.setAccessible(true);f.setInt(screen,value);}
    private static Object field(String name)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(name);f.setAccessible(true);return f.get(screen);}
    private static void refresh(){var mc=Minecraft.getInstance();screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
    private static void click(String label){var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow();check(b.active,"Button active: "+label);screen.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);}
    private static void invoke(String name,Class<?> type,Object value)throws Exception{var m=WorkspaceScreen.class.getDeclaredMethod(name,type);m.setAccessible(true);m.invoke(screen,value);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyUndo")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;
        if(shot!=null&&--shotTicks<=0){Screenshot.grab(mc.gameDirectory,"abyss-v031-"+shot+".png",mc.getMainRenderTarget(),c->{});shot=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(net.neoforged.fml.ModList.get().getModContainerById("kubejs_editor").orElseThrow().getModInfo().getVersion().toString().equals("0.11.0"),"Updated mod metadata is loaded");
                for(String mod:List.of("jei","create","aeronautics","sable","ftblibrary","inventoryprofilesnext"))check(net.neoforged.fml.ModList.get().isLoaded(mod),"Compatibility mod loaded: "+mod);
                baseline=EditorRuntime.recipes().stream().filter(r->r.id().equals(OAK)).findFirst().orElseThrow().data();check(crafts(OAK,Items.OAK_PLANKS),"Original oak button uses oak planks");
                var review=EditorRuntime.review();check(!review.restartRequired(),"Loaded fixtures do not need restart");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<40)return;
            if(ticks>1800)throw new AssertionError("Timed out at phase "+phase+" · "+EditorRuntime.activationError);
            switch(phase){
                case 2 -> {if(result==null)return;ready();apply(recipe(OAK,"minecraft:iron_ingot","replace"));}
                case 3 -> {if(result==null)return;ready();check(crafts(OAK,Items.IRON_INGOT)&&!crafts(OAK,Items.OAK_PLANKS),"First replacement really uses iron");apply(recipe(OAK,"minecraft:diamond","replace"));}
                case 4 -> {
                    if(result==null)return;ready();check(crafts(OAK,Items.DIAMOND),"Second replacement really uses diamond");
                    EditorRuntime.project().recipes.put(OAK,recipe(OAK,"minecraft:emerald","replace"));
                    var invalid=recipe("kubejs_editor:untouched_invalid","minecraft:air","add");invalid.data.add("result",JsonNull.INSTANCE);EditorRuntime.project().recipes.put(invalid.id,invalid);
                    var item=new EditorProject.ItemDraft();item.id="kubejs:untouched_registration";EditorRuntime.project().items.put(item.id,item);
                    var raw=new EditorProject.ScriptDraft();raw.path="kubejs/server_scripts/untouched.js";raw.baseHash="-";raw.text="this is invalid JS {{{";EditorRuntime.project().scripts.put(raw.path,raw);
                    var entry=CreateConfigs.list().stream().filter(e->e.valueType()==Integer.class&&e.current() instanceof Number&&e.spec().test(((Number)e.current()).intValue()+1)).findFirst().orElseThrow();CreateConfigs.queue(entry,String.valueOf(((Number)entry.current()).intValue()+1));configFile=entry.file();config=Files.readAllBytes(configFile);
                    EditorRuntime.saveDraft();pendingText=ProjectStore.JSON.toJson(EditorRuntime.project());startup=Files.readAllBytes(root().resolve(WorkspaceState.GENERATED.get(1)));
                    existing(OAK);check(field("rows").toString().contains("已替换")&&field("rows").toString().contains("已生效"),"Existing list separates replacement label and activation state");capture("replacement");
                }
                case 5 -> {invoke("selectAppliedRecipe",EditorProject.RecipeDraft.class,EditorRuntime.state().applied.recipes.get(OAK));click("撤销此条修改");}
                case 6 -> {
                    if(EditorRuntime.busy()||EditorRuntime.state().activation!=null)return;
                    check(EditorRuntime.activationError.isBlank(),"UI undo completed successfully");check(crafts(OAK,Items.OAK_PLANKS)&&!crafts(OAK,Items.IRON_INGOT)&&!crafts(OAK,Items.DIAMOND),"Revocation restores the original planks, not the previous iron edit");
                    check(!EditorRuntime.state().applied.recipes.containsKey(OAK),"Replacement record disappears instead of becoming a disable record");
                    check(pendingText.equals(ProjectStore.JSON.toJson(EditorRuntime.project())),"All pending work, including the same ID, remains byte-for-byte unchanged");
                    check(Arrays.equals(startup,Files.readAllBytes(root().resolve(WorkspaceState.GENERATED.get(1))))&&Arrays.equals(config,Files.readAllBytes(configFile)),"Revocation does not apply startup scripts or configuration drafts");
                    check(!Files.exists(root().resolve("kubejs/server_scripts/untouched.js"))&&!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("kubejs:untouched_registration")),"Unrelated script and registration drafts were not applied");
                    for(var op:new ArrayList<>(EditorRuntime.operations()))EditorRuntime.cancel(op);
                    var disable=new EditorProject.RecipeDraft(OAK,baseline);disable.operation="remove";apply(disable);
                }
                case 7 -> {if(result==null)return;ready();check(!exists(OAK),"Disabling really prevents this recipe");existing(OAK);check(field("rows").toString().contains("已禁用"),"Disabled rule is labelled explicitly");capture("disabled");}
                case 8 -> {existing(OAK);var b=screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals("×")&&v.getWidth()==18).map(c->(Button)c).findFirst().orElseThrow();check(b.active,"Existing-row revoke button is active");screen.mouseClicked(b.getX()+5,b.getY()+5,0);check(mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen,"Applied removal requests UI confirmation");mc.screen.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals("确认撤销")).map(c->(Button)c).findFirst().orElseThrow().onPress();}
                case 9 -> {if(EditorRuntime.busy()||EditorRuntime.state().activation!=null)return;check(crafts(OAK,Items.OAK_PLANKS)&&!EditorRuntime.state().applied.recipes.containsKey(OAK)&&EditorRuntime.project().recipes.isEmpty(),"Undoing disable restores the original and leaves no pending or active rule");apply(recipe(NEW,"minecraft:iron_ingot","add"));}
                case 10 -> {if(result==null)return;ready();check(exists(NEW)&&EditorRuntime.state().applied.recipes.get(NEW).actionLabel().equals("新增"),"New recipe is labelled as added");apply(recipe(NEW,"minecraft:diamond","replace"));}
                case 11 -> {if(result==null)return;ready();check(crafts(NEW,Items.DIAMOND)&&EditorRuntime.state().applied.recipes.get(NEW).actionLabel().equals("新增"),"Editing a newly created recipe retains its origin label");undo(NEW);}
                case 12 -> {
                    if(result==null)return;ready();check(!exists(NEW)&&!EditorRuntime.state().applied.recipes.containsKey(NEW)&&EditorRuntime.project().recipes.isEmpty(),"Undoing an edited new recipe removes it without a disable placeholder");
                    String path=EditorRuntime.state().outputPath(WorkspaceState.GENERATED.getFirst());byte[] original=Files.readAllBytes(root().resolve(path));Files.writeString(root().resolve(path),new String(original,java.nio.charset.StandardCharsets.UTF_8)+"\n// external edit\n");
                    boolean blocked=false;try{undo(EditorRuntime.state().applied.recipes.keySet().iterator().next());}catch(java.io.IOException expected){blocked=true;}check(blocked&&Files.readString(root().resolve(path)).endsWith("// external edit\n"),"External edits block revocation without overwriting source");Files.write(root().resolve(path),original);apply(recipe(FAIL,"minecraft:iron_ingot","add"));
                }
                case 13 -> {if(result==null)return;ready();Files.writeString(root().resolve(ERROR),"ServerEvents.recipes(e => { throw new Error('ABYSS_UNDO_EXPECTED_ERROR') })\n");undo(FAIL);}
                case 14 -> {
                    if(result==null)return;check(!result.success()&&EditorRuntime.state().applied.recipes.containsKey(FAIL)&&EditorRuntime.state().activation.revokedRecipes.contains(FAIL),"Failed reload retains the active record and revocation transaction");
                    check(EditorRuntime.project().recipes.isEmpty(),"Failed revocation does not create a disable draft");Files.delete(root().resolve(ERROR));EditorRuntime.rollback();result=null;EditorRuntime.reloadResult(false,r->result=r);
                }
                case 15 -> {if(result==null)return;ready();check(exists(FAIL)&&EditorRuntime.state().applied.recipes.containsKey(FAIL),"Rollback restores the previous active rule");undo(FAIL);}
                case 16 -> {
                    if(result==null)return;ready();check(!exists(FAIL),"Revocation retries successfully after rollback");
                    // Arrange a minimal authoring state to exercise deletion of the final owned recipe file.
                    EditorRuntime.state().applied.recipes.clear();apply(recipe(LAST,"minecraft:iron_ingot","add"));
                }
                case 17 -> {if(result==null)return;ready();check(exists(LAST),"Last owned rule is loaded");undo(LAST);}
                case 18 -> {
                    if(result==null)return;ready();check(!exists(LAST)&&EditorRuntime.state().applied.recipes.isEmpty()&&EditorRuntime.project().recipes.isEmpty(),"Revoking the last rule leaves no recipe records");
                    check(!Files.exists(root().resolve(EditorRuntime.state().outputPath(WorkspaceState.GENERATED.getFirst()))),"Empty managed recipe script is removed");
                    check(EditorRuntime.review().changes().isEmpty(),"Repeating apply after revocation is a no-op");
                    check(!ScriptIndex.recipeSources().containsKey(LAST),"Deleting the final script clears previous recipe-source events");existing("");check(!field("rows").toString().contains(LAST),"Revoked recipe does not return as an old script-source row");capture("revoked");
                }
                case 19 -> {Files.writeString(root().resolve("kubejs/server_scripts/undo_manual_source.js"),"ServerEvents.recipes(e => { e.shapeless('minecraft:oak_button', ['minecraft:oak_planks']).id('kubejs_editor:undo_external') })\n");result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 20 -> {if(result==null)return;ready();check(crafts("kubejs_editor:undo_external",Items.OAK_PLANKS)&&ScriptIndex.recipeSources().containsKey("kubejs_editor:undo_external"),"Handwritten source recipe and provenance load normally");var d=recipe("kubejs_editor:undo_external","minecraft:iron_ingot","replace");d.origin="existing";apply(d);}
                case 21 -> {if(result==null)return;ready();check(crafts("kubejs_editor:undo_external",Items.IRON_INGOT),"Editor can replace a handwritten source recipe");undo("kubejs_editor:undo_external");}
                case 22 -> {if(result==null)return;ready();check(crafts("kubejs_editor:undo_external",Items.OAK_PLANKS)&&!EditorRuntime.state().applied.recipes.containsKey("kubejs_editor:undo_external"),"Revocation restores the handwritten source without a new editor rule");check(Files.readString(root().resolve("kubejs/server_scripts/undo_manual_source.js")).contains("['minecraft:oak_planks']"),"Handwritten source content is preserved");Files.delete(root().resolve("kubejs/server_scripts/undo_manual_source.js"));result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 23 -> {if(result==null)return;ready();check(!exists("kubejs_editor:undo_external")&&!ScriptIndex.recipeSources().containsKey("kubejs_editor:undo_external"),"Returning to zero scripts also clears handwritten recipe provenance");Files.writeString(root().resolve("undo-verification.json"),"{\"passed\":true,\"checks\":"+checks+"}");mc.stop();phase=99;return;}
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("undo-verification.json"),"{\"passed\":false,\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
