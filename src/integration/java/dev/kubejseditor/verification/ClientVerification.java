package dev.kubejseditor.verification;

import dev.kubejseditor.client.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Full in-game exercise; all files and worlds remain under run-v2. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientVerification {
    private static int phase,ticks,checks;private static WorkspaceScreen screen;
    private static EditorRuntime.ReloadResult result;private static String manual="kubejs/server_scripts/manual_probe.js",originalTaken;
    private static String targetId,captureName;private static int captureDelay;private static boolean compatibilityChecked;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyClient"))return;
        Minecraft mc=Minecraft.getInstance();ticks++;if(captureName!=null&&--captureDelay<=0){Screenshot.grab(mc.gameDirectory,"abyss-v3-"+captureName+".png",mc.getMainRenderTarget(),c->{});captureName=null;}
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){phase=1;mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                if(!compatibilityChecked&&Files.exists(root().resolve("compatibility-dependencies.json"))){
                    for(String mod:List.of("aeronautics","simulated","offroad","sable","create_aeronautics_toolgun","createjeicompat"))check(net.neoforged.fml.ModList.get().isLoaded(mod),"Compatibility mod loaded: "+mod);
                    check(net.neoforged.fml.ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString().equals("19.69.0.457"),"Updated JEI version loaded");
                    compatibilityChecked=true;
                }
                if(Files.exists(root().resolve("client-stage2.json"))){
                    check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unapplied_restart_probe"),"Unapplied operation restored after a real client restart");
                    EditorRuntime.cancel(new dev.kubejseditor.model.PendingOperation("recipe","kubejs_editor:unapplied_restart_probe","配方生成"));
                    check(EditorRuntime.project().recipes.isEmpty(),"Restored pending operation can be cancelled independently");
                    Files.writeString(root().resolve("client-stage3.json"),"{\"checks\":"+checks+",\"passed\":true}");
                    Files.writeString(root().resolve("client-verification.txt"),"PASS stage3: real restart restored unapplied operation; cancellation persisted.\n");mc.stop();phase=99;return;
                }
                if(Files.exists(root().resolve("client-stage1.json"))){
                    if(EditorRuntime.state().activation!=null){if(ticks>400)throw new AssertionError("Restart activation was not promoted: "+EditorRuntime.logs());return;}
                    check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("kubejs:restart_probe")),"New item registered after restart");
                    check(EditorRuntime.project().items.isEmpty(),"Restart success cleared item drafts");check(EditorRuntime.project().configs.isEmpty(),"Restart success cleared configuration drafts");
                    check(EditorRuntime.state().applied.items.containsKey("kubejs:restart_probe"),"Restarted item retained in active state");
                    check(!EditorRuntime.requiresRestart(),"Restart marker is scoped to the original process");
                    screen=new WorkspaceScreen();mc.setScreen(screen);set("tab",4);refresh();
                    capture("existing");phase=18;ticks=0;return;
                }
                var item=BuiltInRegistries.ITEM.get(ResourceLocation.parse("kubejs:abyss_test_item")).getDefaultInstance();
                check(item.getHoverName().getString().equals("深渊测试材料"),"Chinese item name loaded");
                screen=new WorkspaceScreen();mc.setScreen(screen);
                check(screen.children().stream().filter(c->c instanceof Button).map(c->((Button)c).getMessage().getString()).anyMatch(t->t.contains("新增功能")),"Simplified navigation is visible");
                check(screen.children().stream().filter(c->c instanceof Button).map(c->((Button)c).getMessage().getString()).noneMatch(t->t.contains("更改预览")),"Preview is no longer a main navigation tab");
                check(!net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.isInstance(screen),"Workspace is not an inventory screen");
                check(ScriptIndex.serverGeneration>0&&ScriptIndex.clientGeneration>0,"KubeJS plugin records actual load generations");
                invoke("newRecipe");click(mc.screen,"工作台 · 有序");check(((EditorProject.RecipeDraft)get("recipe")).data.getAsJsonArray("pattern").size()==3,"New crafting grid defaults to 3x3");capture("crafting");phase=2;ticks=0;return;
            }
            if(phase>=2&&phase<99&&ticks>35){
                switch(phase){
                    case 2 -> {
                        var runtime=WorkspaceJeiPlugin.runtime;check(runtime!=null,"JEI available on workspace");
                        Files.writeString(root().resolve("jei-screen-debug.txt"),"properties="+runtime.getScreenHelper().getGuiProperties(screen)+" overlay="+runtime.getIngredientListOverlay().isListDisplayed()+" screen="+mc.screen.getClass());
                        check(runtime.getIngredientListOverlay().isListDisplayed(),"JEI item panel is visible on plain Screen");
                        var before=mc.player.getInventory().getItem(0).copy();var ghost=screen.ghostTargets().stream().filter(g->!g.fluid()).findFirst().orElseThrow();ghost.accept().accept(new ItemStack(Items.GOLD_INGOT));
                        check(ItemStack.matches(before,mc.player.getInventory().getItem(0)),"JEI virtual drop consumes no inventory");
                        invoke("deleteRecipe");select("create:sequenced_assembly");set("tab",2);refresh();capture("sequence");
                        var names=RecipeNames.numbered(Map.of("test:b","铁锭","test:a","铁锭"));check(names.get("test:a").equals("铁锭_1"),"Chinese duplicate numbering");
                    }
                    case 3 -> {
                        var invalid=new EditorProject.RecipeDraft("kubejs_editor:cutting_limit_probe",RecipeAdapters.get("create:cutting").newRecipe());
                        while(invalid.data.getAsJsonArray("results").size()<6)invalid.data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget',count:1}"));
                        EditorRuntime.project().recipes.put(invalid.id,invalid);selectDraft(invalid);
                        check(EditorRuntime.validationIssues().stream().anyMatch(i->i.key().equals("recipe:"+invalid.id)&&i.message().contains("最多允许 4")),"Native workspace validation identifies the exact invalid cutting operation");
                        var add=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("+ 产物")).map(c->(Button)c).findFirst().orElseThrow();
                        check(!add.active,"Over-capacity cutting output cannot be extended");
                        click(screen,"一键应用");check(EditorRuntime.state().activation==null&&EditorRuntime.project().recipes.containsKey(invalid.id),"One-click blocks the whole invalid batch without losing its draft");
                        click(screen,"查看详情");check(!((List<?>)get("issues")).isEmpty(),"Optional details remains available when validation fails");capture("validation");
                        set("tab",2);selectDraft(invalid);while(invalid.data.getAsJsonArray("results").size()>4)invalid.data.getAsJsonArray("results").remove(4);EditorRuntime.project().recipes.put(invalid.id,invalid);selectDraft(invalid);
                        check(EditorRuntime.validationIssues().stream().noneMatch(i->i.key().equals("recipe:"+invalid.id)),"Reducing six outputs to four repairs the actual native codec validation");
                        invoke("cancelOperation",new Class[]{dev.kubejseditor.model.PendingOperation.class},new dev.kubejseditor.model.PendingOperation("recipe",invalid.id,"配方生成"));
                        screen.onClose();mc.setScreen(screen);check(!EditorRuntime.project().recipes.containsKey(invalid.id),"Cancel survives autosave and reopening");
                        var fresh=new EditorProject.RecipeDraft("kubejs_editor:unused_draft",RecipeAdapters.get("create:pressing").newRecipe());EditorRuntime.project().recipes.put(fresh.id,fresh);selectDraft(fresh);invoke("deleteRecipe");
                        check(!EditorRuntime.project().recipes.containsKey(fresh.id)&&get("recipe")==null,"Deleting unsubmitted draft removes row and selection");
                        screen.onClose();mc.setScreen(screen);check(!EditorRuntime.project().recipes.containsKey(fresh.id),"Closing does not resurrect deleted selection");
                        var existing=EditorRuntime.recipes().stream().filter(r->r.id().equals("minecraft:stick")).findFirst().orElseThrow();invoke("selectLoaded",new Class[]{EditorRuntime.Loaded.class},existing);invoke("deleteRecipe");
                        check(EditorRuntime.project().recipes.get("minecraft:stick").operation.equals("remove"),"Existing recipe deletion becomes a pending removal");
                        check(get("recipe")==null,"Existing deletion clears selected recipe immediately");
                        check(EditorRuntime.operations().stream().anyMatch(op->op.id().equals("minecraft:stick")&&op.title().equals("配方禁用")),"Pending recipe deletion is visible in the operation log");
                        invoke("cancelOperation",new Class[]{dev.kubejseditor.model.PendingOperation.class},new dev.kubejseditor.model.PendingOperation("recipe","minecraft:stick","配方删除"));
                        check(!EditorRuntime.project().recipes.containsKey("minecraft:stick")&&EditorRuntime.recipes().stream().anyMatch(r->r.id().equals("minecraft:stick")),"Cancel deletion restores the existing loaded recipe");
                        invoke("selectLoaded",new Class[]{EditorRuntime.Loaded.class},existing);invoke("deleteRecipe");

                        mc.setScreen(new TextScreen(screen,"代码清晰度验证","// Chinese text must remain sharp\nServerEvents.recipes(event => {\n  event.remove({id: 'minecraft:stick'});\n});"));capture("code");}
                    case 4 -> {mc.options.guiScale().set(3);mc.resizeDisplay();check(mc.screen instanceof TextScreen,"Code preview remains available at GUI scale 3");capture("code-scale3");}
                    case 5 -> {mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(screen);set("tab",4);set("existingMode",1);refresh();capture("scripts");check(EditorScripts.list().size()>=3,"All script stages listed");}
                    case 6 -> {
                        var review=EditorRuntime.review();check(!review.restartRequired()&&!review.resourcesChanged(),"Recipe-only application does not request restart or resource/shader refresh");
                        click(screen,"一键应用");result=null;
                    }
                    case 7 -> {
                        if(EditorRuntime.busy()||EditorRuntime.state().activation!=null){if(ticks>400)throw new AssertionError("One-click failed: "+EditorRuntime.activationError);return;}check(EditorRuntime.activationError.isBlank(),"One-click applied and verified the whole pending batch");
                        check(EditorRuntime.project().recipes.isEmpty()&&EditorRuntime.project().items.isEmpty(),"Successful reload clears submitted visual drafts");
                        check(!EditorRuntime.state().applied.recipes.isEmpty(),"Active recipes retained after clearing drafts");
                        check(mc.level.getRecipeManager().byKey(ResourceLocation.parse("minecraft:stick")).isEmpty(),"Actual removed recipe absent on client");
                        check(EditorRuntime.review().changes().isEmpty(),"Repeated save produces no changes");
                        Files.writeString(EditorScripts.checked(root(),manual),"// initial external script\n");
                        mc.setScreen(new ScriptEditorScreen(screen,manual));var buffer=field(mc.screen,"buffer");buffer.getClass().getMethod("replace",String.class).invoke(buffer,"ServerEvents.recipes(e => { e.shapeless('minecraft:paper', ['minecraft:stick']).id('kubejs_editor:manual_probe') })\n");
                        click(mc.screen,"暂存草稿");check(EditorRuntime.project().scripts.containsKey(manual),"Native text editor stages JS");check(Files.readString(root().resolve(manual)).startsWith("// initial"),"Staging text does not activate it");capture("text-editor");
                    }
                    case 8 -> {mc.setScreen(screen);var review=EditorRuntime.review();check(!review.restartRequired()&&!review.resourcesChanged(),"Raw server recipe edits use recipe reload only");EditorRuntime.apply(review);result=null;var server=mc.getSingleplayerServer();server.execute(()->server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((v,e)->mc.execute(()->result=new EditorRuntime.ReloadResult(e==null,"external reload"))));}
                    case 9 -> {
                        if(result==null||EditorRuntime.state().activation!=null&&ticks<200)return;check(result.success(),"Handwritten JS loaded: "+result.message());
                        check(mc.level.getRecipeManager().byKey(ResourceLocation.parse("kubejs_editor:manual_probe")).isPresent(),"Handwritten recipe actually loaded");
                        check(EditorRuntime.project().scripts.isEmpty(),"Text drafts clear after successful load");
                        String before=Files.readString(root().resolve(manual));var stale=EditorScripts.open(manual);stale.text+="// draft edit\n";EditorScripts.stage(stale);
                        Files.writeString(root().resolve(manual),before+"// external edit\n");boolean conflict=false;try{EditorRuntime.review();}catch(java.io.IOException expected){conflict=true;}
                        check(conflict&&Files.readString(root().resolve(manual)).endsWith("// external edit\n"),"External JS edits block overwrite");Files.writeString(root().resolve(manual),before);EditorRuntime.project().scripts.clear();
                        var draft=EditorScripts.open(manual);draft.text="ServerEvents.recipes(e => { throw new Error('ABYSS_EXPECTED_ERROR') })\n";EditorScripts.stage(draft);
                        var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                    }
                    case 10 -> {
                        if(result==null)return;check(!result.success(),"This reload's KubeJS event failure is detected");check(EditorRuntime.project().scripts.containsKey(manual),"Failed reload retains text draft");
                        boolean cancelBlocked=false;try{EditorRuntime.cancel(new dev.kubejseditor.model.PendingOperation("script",manual,"源码修改"));}catch(IllegalStateException expected){cancelBlocked=true;}
                        check(cancelBlocked,"Failed written batch blocks individual cancellation until rollback");
                        EditorRuntime.rollback();EditorRuntime.project().scripts.clear();EditorRuntime.saveDraft();result=null;EditorRuntime.reloadResult(false,r->result=r);
                    }
                    case 11 -> {
                        if(result==null)return;check(result.success(),"Rollback and retry ignore historical errors: "+result.message());
                        String base=WorkspaceState.GENERATED.getFirst();var draft=EditorScripts.open(base);originalTaken=draft.text+"\n// retained manual ownership\n";draft.text=originalTaken;draft.takeover=true;EditorScripts.stage(draft);
                        var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                    }
                    case 12 -> {
                        if(result==null)return;check(result.success(),"Generated script can become handwritten: "+result.message());
                        check(EditorRuntime.state().manualFiles.contains(WorkspaceState.GENERATED.getFirst()),"Manual ownership persisted");
                        var existing=EditorRuntime.state().applied.recipes.values().stream().filter(r->r.data!=null&&r.data.get("type").getAsString().equals("create:pressing")).findFirst().orElseThrow();
                        var draft=new EditorProject.RecipeDraft(existing.id,existing.data);draft.operation="replace";draft.data.getAsJsonArray("results").get(0).getAsJsonObject().addProperty("count",2);targetId=draft.id;EditorRuntime.project().recipes.put(draft.id,draft);
                        var review=EditorRuntime.review();check(review.changes().stream().anyMatch(c->c.path().getFileName().toString().contains("overrides")),"Later visual change generates a separate overlay");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                    }
                    case 13 -> {
                        if(result==null)return;check(result.success(),"Visual overlay applied after manual takeover: "+result.message());
                        check(Files.readString(root().resolve(WorkspaceState.GENERATED.getFirst())).equals(originalTaken),"Visual save does not overwrite handwritten file");
                        check(mc.level.getRecipeManager().byKey(ResourceLocation.parse(targetId)).orElseThrow().value().getResultItem(mc.level.registryAccess()).getCount()==2,"Overlay actually changed output count");
                        check(EditorRuntime.review().changes().isEmpty(),"Manual takeover and overlays remain idempotent");
                        var runtime=WorkspaceJeiPlugin.runtime;check(runtime!=null,"JEI runtime available");
                        var category=runtime.getRecipeManager().getRecipeCategory(mezz.jei.api.constants.RecipeTypes.CRAFTING);
                        var recipe=mc.level.getRecipeManager().byKey(ResourceLocation.parse("kubejs_editor:test_0")).orElseThrow();
                        @SuppressWarnings("unchecked") var crafting=(net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>)(Object)recipe;
                        runtime.getRecipesGui().showRecipes(category,List.of(crafting),List.of());capture("jei-button");
                    }
                    case 14 -> {
                        var layouts=field(mc.screen,"layouts");var list=(List<?>)field(layouts,"recipeLayoutsWithButtons");var buttons=(List<?>)field(list.getFirst(),"buttons");
                        mezz.jei.api.gui.buttons.IIconButtonController editor=null;
                        for(Object b:buttons){var controller=(mezz.jei.api.gui.buttons.IIconButtonController)field(b,"controller");if(dev.kubejseditor.client.JeiRecipeButtons.isEditorController(controller))editor=controller;}
                        check(editor!=null,"JEI attached our edit button to the displayed recipe layout");
                        editor.onPress(new mezz.jei.api.gui.inputs.IJeiUserInput(){public com.mojang.blaze3d.platform.InputConstants.Key getKey(){return com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);}public int getModifiers(){return 0;}public boolean isSimulate(){return false;}public boolean is(net.minecraft.client.KeyMapping key){return false;}public boolean is(mezz.jei.api.runtime.IJeiKeyMapping key){return false;}});
                    }
                    case 15 -> {
                        check(mc.screen instanceof WorkspaceScreen,"JEI edit button opened the workspace");screen=(WorkspaceScreen)mc.screen;check(((EditorProject.RecipeDraft)get("recipe")).id.equals("kubejs_editor:test_0"),"JEI edit button selected the exact ID");check(!(boolean)get("readOnly"),"JEI shaped recipe is editable using authoritative server codec data");capture("jei-editor");
                    }
                    case 16 -> {
                        var loaded=EditorRuntime.recipes().stream().filter(r->r.id().equals("kubejs_editor:manual_probe")).findFirst().orElseThrow();
                        var removal=new EditorProject.RecipeDraft(loaded.id(),loaded.data());removal.operation="remove";EditorRuntime.project().recipes.put(removal.id,removal);
                        var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                    }
                    case 17 -> {
                        if(result==null)return;check(result.success(),"Deleting a handwritten recipe succeeded: "+result.message());
                        check(mc.level.getRecipeManager().byKey(ResourceLocation.parse("kubejs_editor:manual_probe")).isEmpty(),"Recipe introduced by earlier handwritten callback is really removed");
                        var config=dev.kubejseditor.config.CreateConfigs.list().stream().filter(e->e.editable()&&e.valueType()==Integer.class&&e.current() instanceof Number&&e.spec().test(((Number)e.current()).intValue()+1)).findFirst().orElseThrow();
                        dev.kubejseditor.config.CreateConfigs.queue(config,String.valueOf(((Number)config.current()).intValue()+1));
                        var d=new EditorProject.ItemDraft();d.id="kubejs:restart_probe";d.name="重启验证物品";EditorRuntime.project().items.put(d.id,d);var review=EditorRuntime.review();check(review.restartRequired(),"New registration requires restart");EditorRuntime.apply(review);
                        boolean blocked=false;try{EditorRuntime.reload(false,r->{});}catch(java.io.IOException expected){blocked=true;}check(blocked,"Premature reload is blocked until restart");
                        check(EditorRuntime.project().items.containsKey(d.id)&&EditorRuntime.state().activation!=null,"Restart-required transaction is durable");
                        check(EditorRuntime.operations().isEmpty(),"Restart submissions leave the pending list and are represented by the activation transaction");
                        boolean resubmitBlocked=false;try{EditorRuntime.review();}catch(java.io.IOException expected){resubmitBlocked=true;}check(resubmitBlocked,"Restart transaction blocks another submission");
                        set("tab",4);set("existingMode",1);refresh();check(((List<?>)get("rows")).toString().contains("等待重启"),"Existing modifications display waiting registration");capture("waiting-restart");
                        Files.writeString(root().resolve("client-stage1.json"),"{\"checks\":"+checks+",\"passed\":true}");Files.writeString(root().resolve("client-verification.txt"),"PASS stage1: "+checks+" client checks; restart verification pending.\n");phase=70;ticks=0;return;
                    }
                    case 18 -> {
                        var d=new EditorProject.RecipeDraft("kubejs_editor:invalid_ui_probe",RecipeAdapters.get("create:cutting").newRecipe());while(d.data.getAsJsonArray("results").size()<6)d.data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:iron_nugget'}"));
                        EditorRuntime.project().recipes.put(d.id,d);selectDraft(d);set("tab",3);refresh();capture("validation");
                        check(!((List<?>)get("issues")).isEmpty(),"Invalid recipe details remain readable after restarting");
                    }
                    case 19 -> {
                        EditorRuntime.cancel(new dev.kubejseditor.model.PendingOperation("recipe","kubejs_editor:invalid_ui_probe","配方生成"));
                        screen=new WorkspaceScreen();mc.setScreen(screen);set("tab",1);invoke("browseItems");
                        check(mc.screen instanceof ChoiceScreen,"Existing items open a searchable registry selector");mc.screen.onClose();
                        invoke("browseRecipes",new Class[]{boolean.class},false);check(mc.screen instanceof ChoiceScreen,"Existing recipes open a searchable loaded selector");mc.screen.onClose();
                        var item=new EditorProject.ItemDraft();item.id="minecraft:iron_ingot";item.register=false;item.name="";item.texture="";item.properties.addProperty("maxStackSize",32);EditorRuntime.project().items.put(item.id,item);invoke("selectItem",new Class[]{EditorProject.ItemDraft.class},item);
                        invoke("cancelOperation",new Class[]{dev.kubejseditor.model.PendingOperation.class},new dev.kubejseditor.model.PendingOperation("item",item.id,"物品修改"));
                        check(!EditorRuntime.project().items.containsKey(item.id)&&get("item")!=null,"Cancelling existing item modification restores its loaded view without a pending operation");
                        var d=new EditorProject.ItemDraft();d.id="kubejs:cancel_reference_probe";EditorRuntime.project().items.put(d.id,d);
                        var recipe=new EditorProject.RecipeDraft("kubejs_editor:reference_probe",RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:stick'}],result:{id:'kubejs:cancel_reference_probe'}}"));EditorRuntime.project().recipes.put(recipe.id,recipe);
                        check(EditorRuntime.validationIssues().stream().noneMatch(i->i.key().equals("recipe:"+recipe.id)),"Pending registration is a valid recipe reference");
                        EditorRuntime.cancel(new dev.kubejseditor.model.PendingOperation("item",d.id,"物品注册"));
                        check(EditorRuntime.project().recipes.containsKey(recipe.id)&&EditorRuntime.validationIssues().stream().anyMatch(i->i.key().equals("recipe:"+recipe.id)),"Cancelling registration preserves and flags dependent recipe");
                        EditorRuntime.cancel(new dev.kubejseditor.model.PendingOperation("recipe",recipe.id,"配方生成"));
                        var pending=new EditorProject.RecipeDraft("kubejs_editor:unapplied_restart_probe",RecipeAdapters.get("create:cutting").newRecipe());EditorRuntime.project().recipes.put(pending.id,pending);EditorRuntime.saveDraft();
                        Files.writeString(root().resolve("client-stage2.json"),"{\"checks\":"+checks+",\"passed\":true}");Files.writeString(root().resolve("client-verification.txt"),"PASS stage2: registration activation, independent cancellation, dependency validation; pending restoration verification next.\n");mc.stop();phase=99;return;
                    }
                    case 70 -> {mc.stop();phase=99;return;}
                }
                phase++;ticks=0;
            }
            if(ticks>1800&&phase<99)throw new IllegalStateException("Timed out at phase "+phase);
        }catch(Throwable e){try{Files.writeString(mc.gameDirectory.toPath().resolve("client-verification.txt"),"FAIL phase="+phase+" checks="+checks+" "+e);}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void capture(String name){captureName=name;captureDelay=8;}
    private static Object field(Object object,String name)throws Exception{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Object get(String name)throws Exception{return field(screen,name);}
    private static void set(String name,int value)throws Exception{var f=WorkspaceScreen.class.getDeclaredField(name);f.setAccessible(true);f.setInt(screen,value);}
    private static void refresh(){var mc=Minecraft.getInstance();screen.init(mc,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
    private static void invoke(String name)throws Exception{invoke(name,new Class[0]);}
    private static void invoke(String name,Class<?>[] types,Object... values)throws Exception{var m=WorkspaceScreen.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(screen,values);}
    private static void selectDraft(EditorProject.RecipeDraft r)throws Exception{invoke("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},r,false);}
    private static void select(String type)throws Exception{selectDraft(EditorRuntime.project().recipes.values().stream().filter(r->r.data!=null&&r.data.get("type").getAsString().equals(type)).findFirst().orElseThrow());}
    private static void click(Screen target,String label){var b=target.children().stream().filter(c->c instanceof Button button&&button.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow();target.mouseClicked(b.getX()+b.getWidth()/2.0,b.getY()+b.getHeight()/2.0,0);}
}
