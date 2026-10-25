package dev.kubejseditor.verification;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import java.nio.file.*;
import java.util.*;

/** One combined isolated session: public JEI conversion, real UI batch, compensation and rollback. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientBatchVerification {
    private static final String LIVE="bbb:mirror",DISABLED="bbb:disabled",ADDED=GenericCompatFixture.MOD+":batch_added",PENDING=GenericCompatFixture.MOD+":unrelated_pending",OWNED="kubejs_editor:batch_owned";
    private static final Set<String> BATCH=Set.of(LIVE,DISABLED,ADDED);
    private static int phase,ticks,checks;
    private static volatile boolean failNextSync,corruptNextSync;
    private static int syncFailures;
    private static long requests;
    private static RecipeHolder<?> corruptRecipe;
    private static CompatibilityScreen workbench;
    private static AppliedDeletionScreen deletion;
    private static EditorRuntime.ReloadResult result;
    private static final JsonArray notes=new JsonArray();
    private static Minecraft mc(){return Minecraft.getInstance();}
    public static void startInWorld(){phase=1;ticks=0;}
    private static Path root(){return mc().gameDirectory.toPath();}
    private static void check(boolean value,String note){checks++;notes.add(note);if(!value)throw new AssertionError(note);}
    private static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void click(String name){var screen=mc().screen;var button=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().replace("● ","").equals(name)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+name));check(button.active,"Enabled UI action: "+name);screen.mouseClicked(button.getX()+2,button.getY()+2,0);screen.mouseReleased(button.getX()+2,button.getY()+2,0);}
    @SuppressWarnings("unchecked") private static RecipeHolder<GenericCompatFixture.GenericRecipe> holder(String id){return (RecipeHolder<GenericCompatFixture.GenericRecipe>)(Object)mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow();}
    private static boolean exists(String id){return mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static boolean ready(){return !EditorRuntime.busy()&&WorkspaceJeiPlugin.runtime!=null;}
    private static boolean success(){return ready()&&EditorRuntime.state().activation==null&&EditorRuntime.activationError.isBlank();}
    private static void screenshot(String file){if(Boolean.getBoolean("kubejs.editor.captureVerificationImages"))Screenshot.grab(mc().gameDirectory,file,mc().getMainRenderTarget(),message->{});}
    private static EditorProject.RecipeDraft draft(String id,JsonObject data,String operation){var d=new EditorProject.RecipeDraft(id,data.deepCopy());d.operation=operation;d.origin=operation.equals("add")?"add":"existing";d.source=data.deepCopy();d.forceNative=true;d.loadConditionPolicy=LoadConditions.IGNORE;return d;}
    private static mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout(String id){var display=GenericCompatFixture.display(holder(id),mc().getSingleplayerServer().registryAccess());var category=WorkspaceJeiPlugin.runtime.getRecipeManager().getRecipeCategory(GenericCompatFixture.CONVERTED_JEI);return WorkspaceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category,display,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void realConvertedSources()throws Exception{
        var manager=WorkspaceJeiPlugin.runtime.getRecipeManager();
        for(String machine:List.of("CrystalAssembler","CircuitCutter")){
            String categoryId="extendedae_plus:"+(machine.equals("CrystalAssembler")?"crystal_assembler_plus":"circuit_cutter_plus");
            var type=Class.forName("com.extendedae_plus.recipe.Super"+machine+"RecipeManager");
            var recipes=(List<RecipeHolder<?>>)type.getMethod("getAllRecipes",net.minecraft.world.level.Level.class).invoke(null,mc().getSingleplayerServer().overworld());
            var display=recipes.stream().filter(r->r.id().getPath().startsWith("compat/extendedae/")).findFirst().orElseThrow();
            var category=manager.createRecipeCategoryLookup().get().filter(c->c.getRecipeType().getUid().toString().equals(categoryId)).findFirst().orElseThrow();
            var layout=(mezz.jei.api.gui.IRecipeLayoutDrawable<?>)manager.createRecipeLayoutDrawable((mezz.jei.api.recipe.category.IRecipeCategory)category,display,WorkspaceJeiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
            var source=JeiRecipeSources.resolve(layout);
            check(source.id()!=null,"Actual "+categoryId+" converted display resolves a native source: "+source.reason());
            check(!source.notice().isBlank()&&!source.id().equals(display.id()),"Actual converted source warns about shared impact and never writes the synthetic ID");
            JeiRecipeSources.capture(layout,source.id());var nativeRecipe=EditorRuntime.recipe(source.id().toString());
            check(nativeRecipe!=null&&nativeRecipe.data()!=null,"Actual converted source imports its native JSON: "+(nativeRecipe==null?"missing":nativeRecipe.error()));
            EditorRuntime.validateRecipe(draft(source.id().toString(),nativeRecipe.data(),"replace"),Set.of());
            var preview=CompatJeiLayouts.create(source.id().toString(),nativeRecipe.data());
            check(preview.layout()!=null&&preview.layout().getRecipeCategory().getRecipeType().getUid().toString().equals(categoryId),"Actual converted source reconstructs its original JEI machine preview");
            JeiDisplayScreen.open(new WorkspaceScreen(),layout);click("编辑原来源");
            check(mc().screen instanceof CompatibilityScreen actual&&!actual.ghostTargets().isEmpty(),"Actual converted source opens with directly editable JEI slots after generic discovery");
            notes.add("Actual conversion: "+display.id()+" -> "+source.id()+"; slots="+preview.slots().size());
        }
    }
    private static void prepare()throws Exception{
        EditorRuntime.project();SimpleRecipes.setEnabled(EditorRuntime.root(),true);
        realConvertedSources();
        check(EditorRuntime.state().applied.items.containsKey(OWNED),"Prepared owned registration has real editor ownership");
        check(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(OWNED)),"Owned item really registered at startup");
        var definition=new SourceConversion();definition.category=GenericCompatFixture.CONVERTED_JEI.getUid().toString();definition.sourceType=GenericCompatFixture.MOD+":generic";definition.displayType=GenericCompatFixture.MOD+":converted";definition.displayPrefix="compat/source/";definition.label="通用转换验证";ConvertedRecipeSources.register(definition);
        var ambiguous=JeiRecipeSources.resolve(layout("bbb:ambiguous"));check(ambiguous.id()==null&&ambiguous.candidates().size()==2,"Identical candidates under different namespaces require confirmation");
        var display=layout(LIVE);var source=JeiRecipeSources.resolve(display);check(source.id()!=null&&source.id().toString().equals(LIVE),"Full Codec comparison selects bbb source and rejects different components");check(!source.notice().isBlank(),"Conversion explains its shared source impact");
        long indexed=EditorRuntime.lastIndexMs;var parent=new WorkspaceScreen();JeiDisplayScreen.open(parent,display);check(mc().screen instanceof ConfirmScreen,"Actual JEI entry asks to edit the authoritative shared source");click("编辑原来源");check(mc().screen instanceof CompatibilityScreen,"Confirmed converted display opens the common editor");workbench=(CompatibilityScreen)mc().screen;
        check(workbench.draft.id.equals(LIVE)&&workbench.draft.data.get("type").getAsString().equals(definition.sourceType),"Draft uses the native source ID and serializer, not the synthetic display ID");
        check(LoadConditions.present(workbench.draft.source)&&LoadConditions.ignored(workbench.draft),"Unknown native source recovers original conditions before discovery and defaults to ignore");
        var view=(CompatJeiLayouts.View)field(workbench,"view");check(view.layout()!=null&&view.layout().getRecipeCategory().getRecipeType().getUid().equals(GenericCompatFixture.CONVERTED_JEI.getUid()),"Native source JSON rebuilds the converted JEI preview");
        var presentation=(RecipePresentation)field(workbench,"presentation");var input=presentation.editableBindings().stream().filter(b->b.field().kind==CompatDefinition.Kind.ITEM&&!b.output()&&b.path().contains("feedstock")).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var regions=(List<CompatJeiLayouts.BoundRegion>)field(workbench,"regions");var region=regions.stream().filter(r->r.binding().equals(input.key())).findFirst().orElseThrow();var transform=CompatibilityScreen.class.getDeclaredMethod("screenRect",Rect2i.class);transform.setAccessible(true);var area=(Rect2i)transform.invoke(workbench,region.rect());
        var stack=new ItemStack(Items.COPPER_INGOT);var typed=WorkspaceJeiPlugin.runtime.getIngredientManager().createTypedIngredient(stack).orElseThrow();var targets=WorkspaceJeiPlugin.compatibilityGhostHandler().getTargetsTyped(workbench,typed,true);targets.stream().filter(t->CompatJeiLayouts.sameRect(t.getArea(),area)).findFirst().orElseThrow().accept(stack);
        check(workbench.draft.data.getAsJsonObject("feedstock").get("count").getAsInt()==20480,"Actual JEI drop preserves the independent large quantity");check(workbench.draft.data.getAsJsonObject("feedstock").toString().contains("minecraft:copper_ingot"),"Actual registered ghost handler edits the native source");
        check(((CompatJeiLayouts.View)field(workbench,"view")).layout()!=null,"Converted preview survives the source edit");check(EditorRuntime.lastIndexMs==indexed,"Opening and editing one JEI recipe does not rebuild the full catalogue");
        click("暂存");var data=EditorRuntime.recipe(DISABLED).data();EditorRuntime.project().recipes.put(DISABLED,draft(DISABLED,data,"remove"));
        var added=RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'"+OWNED+"'}],result:{id:'minecraft:oak_button',count:2}}");EditorRuntime.project().recipes.put(ADDED,draft(ADDED,added,"add"));EditorRuntime.saveDraft();mc().options.guiScale().set(2);mc().resizeDisplay();
    }
    private static void addUnrelated()throws Exception{var data=RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'missing:unsubmitted'}],result:{id:'minecraft:paper'}}");EditorRuntime.project().recipes.put(PENDING,draft(PENDING,data,"add"));EditorRuntime.saveDraft();}
    private static void showBatch()throws Exception{
        var parent=new WorkspaceScreen();mc().setScreen(parent);var labels=parent.children().stream().filter(c->c instanceof Button).map(c->((Button)c).getMessage().getString().replace("● ","")).toList();check(!labels.contains("批量删除")&&labels.get(4).equals("强制兼容"),"Five main tabs keep force compatibility last and batch deletion nested");click("已有修改");click("批量删除");check(mc().screen instanceof AppliedDeletionScreen,"Applied changes child action opens batch deletion");deletion=(AppliedDeletionScreen)mc().screen;click("配方");requests=EditorRuntime.reloadRequests();click("全选筛选结果");
        check(((Set<?>)field(deletion,"recipes")).size()==3,"Three applied operation types can be selected together");click("[x]");check(((Set<?>)field(deletion,"recipes")).size()==2,"A selected row can be deselected with one click");click("[ ]");
        var search=deletion.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();search.setValue("mirror");check(((Set<?>)field(deletion,"recipes")).size()==3,"Filtering retains selections outside the visible results");deletion.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow().setValue("");
        check(EditorRuntime.reloadRequests()==requests&&EditorRuntime.state().activation==null,"Selecting and filtering never performs a reload");
    }
    private static void confirmBatch(){click("确认删除 3 项");check(mc().screen instanceof ConfirmScreen,"One confirmation covers the complete selected batch");click("确认删除");}
    private static void verifyDeleted(String label)throws Exception{
        check(success(),label+" succeeds without leaving a transaction lock");check(EditorRuntime.reloadRequests()==requests+1,label+" uses exactly one reload request");check(!exists(ADDED)&&exists(LIVE)&&exists(DISABLED),"Added rule disappears, replaced and disabled sources are restored");
        check(holder(LIVE).value().feedstock().ingredient().test(new ItemStack(Items.IRON_INGOT)),"Replaced source returns to its actual original ingredient");check(EditorRuntime.state().applied.recipes.isEmpty(),"All selected recipe ownership is removed together");check(!Files.exists(root().resolve(EditorRuntime.state().outputPath(WorkspaceState.GENERATED.getFirst()))),"Deleting the last recipe batch removes the generated script");check(EditorRuntime.project().recipes.containsKey(PENDING),"Invalid unrelated pending work is neither validated, submitted nor discarded");
    }
    private static void restore()throws Exception{EditorRuntime.rollback();result=null;EditorRuntime.reloadResult(false,r->result=r);}
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event){
        if(!Boolean.getBoolean("kubejs.editor.verifyBatch")||!failNextSync)return;failNextSync=false;syncFailures++;
        if(corruptNextSync){corruptNextSync=false;var manager=mc().getSingleplayerServer().getRecipeManager();var list=new ArrayList<RecipeHolder<?>>(manager.getRecipes());list.removeIf(r->r.id().equals(corruptRecipe.id()));list.add(corruptRecipe);manager.replaceRecipes(list);}
        throw new NullPointerException("Data resource not present: null [batch synchronization verification]");
    }
    private static void finish(Throwable error)throws Exception{
        var report=new JsonObject();report.addProperty("passed",error==null);report.addProperty("checks",checks);report.addProperty("phase",phase);report.addProperty("gameLaunches",1);report.addProperty("reloadRequests",EditorRuntime.reloadRequests());report.addProperty("injectedSyncFailures",syncFailures);report.add("checksDetail",notes);report.addProperty("realRestartTested",false);if(error!=null){var writer=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(writer));report.addProperty("error",writer.toString());}Files.writeString(root().resolve("batch-verification.json"),ProjectStore.JSON.toJson(report));phase=99;mc().stop();
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyBatch")||phase==99||Boolean.getBoolean("kubejs.editor.verifyUiRevision")&&!ClientUiRevisionVerification.finished()||Boolean.getBoolean("kubejs.editor.verifyPatches")&&!ClientPatchVerification.finished())return;ticks++;
        try{
            if(ticks>12000)throw new AssertionError("Timeout at "+phase+" · "+EditorRuntime.activationError);
            if(phase==0&&mc().screen instanceof TitleScreen&&ticks>80){mc().createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc().level!=null&&mc().player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>160){prepare();phase=2;ticks=0;return;}
            if(phase==2&&ticks>20){screenshot("batch-source-scale2.png");click("一键应用");phase=3;ticks=0;return;}
            if(phase==3&&ready()&&ticks>20){check(success(),"JEI-origin add/replace/disable batch really applies: "+EditorRuntime.activationError);check(exists(ADDED)&&!exists(DISABLED),"Native server reflects all initial operations");@SuppressWarnings("unchecked") var crafting=(Recipe<CraftingInput>)mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(ADDED)).orElseThrow().value();check(crafting.matches(CraftingInput.of(1,1,List.of(new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(OWNED))))),mc().getSingleplayerServer().overworld()),"Generated vanilla recipe really matches its owned input");check(holder(LIVE).value().feedstock().ingredient().test(new ItemStack(Items.COPPER_INGOT)),"Applied source contains the material dropped through JEI");check(Files.exists(root().resolve("kubejs/config/kubejs_editor/last-performance.json")),"Reload stage timings persist separately from exceptional diagnostics");check(((CompatJeiLayouts.View)field(workbench,"view")).layout()!=null&&((CompatJeiLayouts.View)field(workbench,"view")).layout().getRecipeCategory().getRecipeType().getUid().equals(GenericCompatFixture.CONVERTED_JEI.getUid()),"Converted preview category is recovered after JEI runtime replacement");addUnrelated();showBatch();mc().options.guiScale().set(2);mc().resizeDisplay();phase=4;ticks=0;return;}
            if(phase==4&&ticks>20){screenshot("batch-delete-scale2.png");mc().options.guiScale().set(3);mc().resizeDisplay();phase=5;ticks=0;return;}
            if(phase==5&&ticks>20){check(((Set<?>)field(deletion,"recipes")).size()==3,"GUI scale 3 preserves all selected rules");screenshot("batch-delete-scale3.png");confirmBatch();phase=6;ticks=0;return;}
            if(phase==6&&ready()&&ticks>20){verifyDeleted("Batch revoke");restore();phase=7;ticks=0;return;}
            if(phase==7&&result!=null&&ready()){check(result.success()&&EditorRuntime.state().applied.recipes.keySet().equals(BATCH),"Rollback restores the complete batch ownership");check(exists(ADDED)&&!exists(DISABLED)&&EditorRuntime.project().recipes.containsKey(PENDING),"Rollback restores native rules and retains unrelated pending work");showBatch();failNextSync=true;confirmBatch();phase=8;ticks=0;return;}
            if(phase==8&&ready()&&ticks>20){verifyDeleted("Compensated batch revoke");var diagnostic=JsonParser.parseString(EditorDiagnostics.read(root())).getAsJsonObject();check(diagnostic.get("outcome").getAsString().equals("compensated-success")&&syncFailures==1,"Third-party synchronization failure compensates the whole batch once");restore();phase=9;ticks=0;return;}
            if(phase==9&&result!=null&&ready()){check(result.success(),"Restoring the compensated batch succeeds");corruptRecipe=mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(ADDED)).orElseThrow();showBatch();failNextSync=true;corruptNextSync=true;confirmBatch();phase=10;ticks=0;return;}
            if(phase==10&&ready()&&ticks>20){check(EditorRuntime.state().activation!=null&&!EditorRuntime.activationError.isBlank(),"Incorrect native result rejects promotion and leaves the failed batch recoverable");check(EditorRuntime.state().applied.recipes.keySet().equals(BATCH)&&EditorRuntime.project().recipes.containsKey(PENDING),"Failure cannot partially promote or discard pending work");restore();phase=11;ticks=0;return;}
            if(phase==11&&result!=null&&ready()){check(result.success()&&exists(ADDED),"Rollback recovers from a genuinely incorrect server result");requests=EditorRuntime.reloadRequests();List<String> blockers=EditorRuntime.deletionBlockers(BATCH,Set.of(OWNED));check(blockers.isEmpty(),"Whole-batch reference checking allows deleting an owned item with its selected added recipe");result=null;EditorRuntime.revokeChanges(BATCH,Set.of(OWNED),r->result=r);check(result!=null&&result.success()&&EditorRuntime.state().activation.restart,"Mixed recipe and registration deletion is staged as one restart transaction");check(EditorRuntime.reloadRequests()==requests,"A restart batch does not perform a redundant intermediate reload");check(EditorRuntime.state().activation.deletedItems.contains(OWNED)&&EditorRuntime.state().activation.revokedRecipes.size()==3,"Mixed activation tracks all registry and recipe members");check(EditorRuntime.project().recipes.containsKey(PENDING),"Mixed staging still preserves unrelated pending work");EditorRuntime.rollback();check(EditorRuntime.requiresRestart()&&Files.exists(root().resolve(WorkspaceState.GENERATED.get(1))),"Mixed rollback restores the startup script and explicitly requires restart");finish(null);}
        }catch(Throwable error){error.printStackTrace();try{finish(error);}catch(Exception write){write.printStackTrace();phase=99;mc().stop();}}
    }
    private ClientBatchVerification(){}
}
