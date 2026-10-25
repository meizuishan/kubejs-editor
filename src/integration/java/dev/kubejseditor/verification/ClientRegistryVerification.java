package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Four real JVM starts: edit+disable, verify+revoke, restore+rollback, verify rollback. No screenshots. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientRegistryVerification {
    private static final int STAGE=Integer.getInteger("kubejs.editor.registryStage",1);
    private static final String RITUAL="forbidden_arcanus:hephaestus_forge/ritual",EDIT="forbidden_arcanus:eternal_stella",DISABLE="forbidden_arcanus:boss_catcher",UNRELATED="kubejs_editor_verification:unrelated_pending";
    private static int phase,ticks,checks;private static final JsonArray notes=new JsonArray();
    private static Minecraft mc(){return Minecraft.getInstance();}
    private static Path root(){return mc().gameDirectory.toPath();}
    private static void check(boolean value,String note){checks++;notes.add(note);if(!value)throw new AssertionError(note);}
    private static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void click(String label){var screen=mc().screen;var button=screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));check(button.active,"Enabled UI: "+label);screen.mouseClicked(button.getX()+2,button.getY()+2,0);screen.mouseReleased(button.getX()+2,button.getY()+2,0);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static IRecipeLayoutDrawable<?> layout(String registry,String id,String category){var runtime=WorkspaceJeiPlugin.runtime;var value=mc().level.registryAccess().registryOrThrow(net.minecraft.resources.ResourceKey.createRegistryKey(ResourceLocation.parse(registry))).get(ResourceLocation.parse(id));var type=runtime.getRecipeManager().createRecipeCategoryLookup().get().filter(c->c.getRecipeType().getUid().toString().equals(category)).findFirst().orElseThrow();return (IRecipeLayoutDrawable<?>)runtime.getRecipeManager().createRecipeLayoutDrawable((mezz.jei.api.recipe.category.IRecipeCategory)type,value,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();}
    private static EditorProject snapshots()throws Exception{return ProjectStore.JSON.fromJson(Files.readString(root().resolve("registry-snapshots.json")),EditorProject.class);}
    private static String key(String registry,String entry){return new RegistryEntryTarget(registry,entry).id();}
    private static void stageOne()throws Exception{
        check(!ModList.get().isLoaded("forbidden_arcanus_js"),"Real Forbidden Arcanus runs without its KubeJS bridge");
        var view=layout(RITUAL,EDIT,"forbidden_arcanus:hephaestus_smithing");var source=JeiRecipeSources.resolve(view);check(source.id()!=null&&source.id().toString().equals(key(RITUAL,EDIT)),"Actual ritual display resolves through the native registry instead of RecipeManager: "+source.reason());
        check(mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(EDIT)).isEmpty(),"The original ritual is not pretending to be a vanilla recipe");
        JeiDisplayScreen.open(new WorkspaceScreen(),view);check(mc().screen instanceof ConfirmScreen,"Native source explains its restart requirement before editing");click("编辑原来源");check(mc().screen instanceof CompatibilityScreen,"Actual JEI entry opens the common editing screen");var screen=(CompatibilityScreen)mc().screen;
        check(screen.draft.registryTarget!=null&&screen.draft.registryTarget.entry.equals(EDIT),"Native destination metadata survives the UI entry");check(((CompatJeiLayouts.View)field(screen,"view")).layout()!=null,"Actual ritual preview rebuilds without a RecipeSerializer bridge");
        var p=(RecipePresentation)field(screen,"presentation");var binding=p.editableBindings().stream().filter(b->b.field().role==CompatDefinition.Role.CENTER).findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var regions=(List<CompatJeiLayouts.BoundRegion>)field(screen,"regions");var region=regions.stream().filter(r->r.binding().equals(binding.key())).findFirst().orElseThrow();var transform=CompatibilityScreen.class.getDeclaredMethod("screenRect",Rect2i.class);transform.setAccessible(true);var area=(Rect2i)transform.invoke(screen,region.rect());
        var stack=new ItemStack(Items.NETHER_STAR);var typed=WorkspaceJeiPlugin.runtime.getIngredientManager().createTypedIngredient(stack).orElseThrow();WorkspaceJeiPlugin.compatibilityGhostHandler().getTargetsTyped(screen,typed,true).stream().filter(t->CompatJeiLayouts.sameRect(t.getArea(),area)).findFirst().orElseThrow().accept(stack);
        check(screen.draft.data.get("main_ingredient").toString().contains("minecraft:nether_star"),"Actual JEI dragging edits the native central ingredient");
        var old=screen.draft.data.get("duration");screen.draft.data.addProperty("duration","invalid");boolean rejected=false;try{EditorRuntime.validateRecipe(screen.draft,Set.of());}catch(RuntimeException expected){rejected=true;}screen.draft.data.add("duration",old);check(rejected,"Invalid native parameter is rejected by the original ritual Codec");
        screen.draft.data.addProperty("duration",1010);screen.draft.data.getAsJsonObject("essences").addProperty("aureal",666);EditorRuntime.validateRecipe(screen.draft,Set.of());click("暂存");
        var disabled=EditorRuntime.recipe(key(RITUAL,DISABLE));check(disabled!=null&&disabled.registryTarget()!=null,"A second real ritual imports independently");var removal=new EditorProject.RecipeDraft(disabled.id(),disabled.data());removal.registryTarget=disabled.registryTarget();removal.source=disabled.data().deepCopy();removal.operation="remove";removal.origin="existing";removal.forceNative=true;EditorRuntime.project().recipes.put(removal.id,removal);
        var generic=layout(RegistryVerificationFixture.KEY.location().toString(),"kubejs_editor_verification:base",RegistryVerificationJei.TYPE.getUid().toString());var genericSource=JeiRecipeSources.resolve(generic);check(genericSource.id()!=null,"An unrelated custom registry resolves without a mod-name rule");var entry=EditorRuntime.recipe(genericSource.id().toString());var program=new EditorProject.RecipeDraft(entry.id(),entry.data());program.registryTarget=entry.registryTarget();program.source=entry.data().deepCopy();program.operation="replace";program.origin="existing";program.forceNative=true;program.data.addProperty("duration",333);EditorRuntime.project().recipes.put(program.id,program);
        Files.writeString(root().resolve("registry-snapshots.json"),ProjectStore.JSON.toJson(EditorRuntime.project()));EditorRuntime.saveDraft();click("一键应用");
        check(EditorRuntime.state().activation!=null&&EditorRuntime.state().activation.restart,"All registry changes stage one restart transaction");check(EditorRuntime.activationError.isBlank()&&EditorRuntime.reloadRequests()==0,"Native apply does not attempt an ineffective ordinary reload");
        for(var d:EditorRuntime.project().recipes.values()){check(Files.exists(root().resolve(d.registryTarget.path())),"Native overlay written: "+d.registryTarget.entry);check(EditorRuntime.state().managedHashes.containsKey(d.registryTarget.path()),"Registry overlay has tracked ownership");}
        check(!Files.exists(root().resolve(WorkspaceState.GENERATED.getFirst())),"No fake KubeJS recipe script was created");
        var invalid=new EditorProject.RecipeDraft(UNRELATED,RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'missing:unsubmitted'}],result:{id:'minecraft:paper'}}"));EditorRuntime.project().recipes.put(UNRELATED,invalid);EditorRuntime.saveDraft();finish(null);
    }
    private static void stageTwo()throws Exception{
        var snapshots=snapshots();check(EditorRuntime.state().applied.recipes.keySet().equals(snapshots.recipes.keySet()),"A real JVM restart verifies and promotes all native registry edits");
        for(var d:snapshots.recipes.values())RegistryDataSources.verify(d,!d.operation.equals("remove"));check(true,"Actual server registries contain the changed inputs, resources and parameters and omit the disabled ritual");
        var view=layout(RITUAL,EDIT,"forbidden_arcanus:hephaestus_smithing");JeiDisplayScreen.open(new WorkspaceScreen(),view);click("编辑原来源");var screen=(CompatibilityScreen)mc().screen;check(screen.draft.data.get("duration").getAsInt()==1010&&!screen.ghostTargets().isEmpty(),"Reopening JEI after restart retains edited data and direct drag targets");
        check(EditorRuntime.project().recipes.containsKey(UNRELATED),"Post-apply unrelated pending work survives promotion");
        final EditorRuntime.ReloadResult[] result={null};EditorRuntime.revokeChanges(snapshots.recipes.keySet(),Set.of(),r->result[0]=r);check(result[0]!=null&&result[0].success()&&EditorRuntime.state().activation.restart,"Batch revoke stages all three registry entries together");check(EditorRuntime.reloadRequests()==0,"Native batch revoke never requests an ordinary recipe reload");check(EditorRuntime.project().recipes.containsKey(UNRELATED),"Invalid unrelated pending work does not block or enter batch revoke");for(var d:snapshots.recipes.values())check(!Files.exists(root().resolve(d.registryTarget.path())),"Batch revoke removes only its owned overlay");finish(null);
    }
    private static void stageThree()throws Exception{
        check(EditorRuntime.state().applied.recipes.isEmpty(),"Restart verifies batch revoke and clears all selected ownership");for(var d:snapshots().recipes.values()){var original=WorkspaceState.copy(d,EditorProject.RecipeDraft.class);original.data=d.source;RegistryDataSources.verify(original,true);}check(true,"Both the edited and disabled originals are truly restored after restart");
        check(EditorRuntime.project().recipes.containsKey(UNRELATED),"Unrelated pending work survives native restore");EditorRuntime.rollback();check(EditorRuntime.requiresRestart(),"Native data rollback correctly requires restart");for(var d:snapshots().recipes.values())check(Files.exists(root().resolve(d.registryTarget.path())),"Rollback restores the previous native overlay file");finish(null);
    }
    private static void stageFour()throws Exception{
        var snapshot=snapshots();for(var d:snapshot.recipes.values())RegistryDataSources.verify(d,!d.operation.equals("remove"));check(true,"Actual native registries return to edited and disabled state after rollback and another JVM restart");check(EditorRuntime.state().applied.recipes.keySet().equals(snapshot.recipes.keySet()),"Rollback restores all native ownership metadata");check(EditorRuntime.project().recipes.containsKey(UNRELATED)&&EditorRuntime.state().activation==null,"Rollback retains unrelated pending work without an activation lock");finish(null);
    }
    private static void stageFive()throws Exception{
        check(ModList.get().getModContainerById("kubejs").orElseThrow().getModInfo().getVersion().toString().equals("2101.7.2-build.374"),"ATM10's actual KubeJS build.374 is loaded");check(ModList.get().getModContainerById("rhino").orElseThrow().getModInfo().getVersion().toString().equals("2101.2.8-build.91"),"ATM10's actual Rhino build.91 is loaded");check(ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString().equals("19.64.0.454"),"ATM10's actual JEI 19.64.0.454 is loaded");
        for(var d:snapshots().recipes.values())RegistryDataSources.verify(d,!d.operation.equals("remove"));check(true,"Exact ATM10 dependency versions load the native edits and disabled ritual");
        var view=layout(RITUAL,EDIT,"forbidden_arcanus:hephaestus_smithing");var source=JeiRecipeSources.resolve(view);check(source.id()!=null,"Exact ATM10 JEI resolves the ritual's registry source");JeiDisplayScreen.open(new WorkspaceScreen(),view);click("编辑原来源");check(mc().screen instanceof CompatibilityScreen screen&&screen.draft.registryTarget!=null&&screen.draft.data.get("duration").getAsInt()==1010&&!screen.ghostTargets().isEmpty(),"Exact ATM10 dependencies open the common editable JEI preview");
        check(EditorRuntime.project().recipes.containsKey(UNRELATED)&&EditorRuntime.state().activation==null,"Read-only smoke verification preserves all pending data and creates no transaction");finish(null);
    }
    private static void finish(Throwable error)throws Exception{var report=new JsonObject();report.addProperty("passed",error==null);report.addProperty("stage",STAGE);report.addProperty("checks",checks);report.addProperty("screenshots",false);report.addProperty("bridgeInstalled",ModList.get().isLoaded("forbidden_arcanus_js"));report.addProperty("reloadRequests",EditorRuntime.reloadRequests());report.add("details",notes);if(error!=null){var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.addProperty("error",text.toString());}Files.writeString(root().resolve("registry-stage"+STAGE+".json"),ProjectStore.JSON.toJson(report));phase=99;mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!Boolean.getBoolean("kubejs.editor.verifyRegistry")||phase==99)return;ticks++;try{
        if(ticks>12000)throw new AssertionError("Registry verification timeout phase="+phase+" error="+EditorRuntime.activationError);
        if(phase==0&&mc().screen instanceof TitleScreen&&ticks>60){mc().createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
        if(phase==1&&mc().level!=null&&mc().player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>180){EditorRuntime.project();phase=2;ticks=0;return;}
        if(phase==2&&ticks>80&&!EditorRuntime.busy()){
            if(STAGE>1&&STAGE<4&&EditorRuntime.state().activation!=null){if(!EditorRuntime.activationError.isBlank())throw new AssertionError(EditorRuntime.activationError);return;}
            phase=3;switch(STAGE){case 1->stageOne();case 2->stageTwo();case 3->stageThree();case 4->stageFour();case 5->stageFive();default->throw new AssertionError("Invalid stage");}
        }
    }catch(Throwable error){error.printStackTrace();try{finish(error);}catch(Exception write){write.printStackTrace();phase=99;mc().stop();}}}
    private ClientRegistryVerification(){}
}
