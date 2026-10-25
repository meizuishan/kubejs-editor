package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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

/** One isolated client session; generic native structures, not per-machine editing fixtures. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientLoadConditionVerification {
    private static final String PREFIX=GenericCompatFixture.MOD+":",LIVE=PREFIX+"conditional_live",SOURCE=PREFIX+"conditional_source",FALSE=PREFIX+"kept_false",TRUE=PREFIX+"kept_true",UNKNOWN=PREFIX+"ignored_unknown",PENDING=PREFIX+"unrelated_pending";
    private static int phase,ticks,checks;private static EditorRuntime.ReloadResult result;private static CompatibilityScreen screen;
    private static final JsonObject report=new JsonObject();private static final JsonArray notes=new JsonArray();
    private static Minecraft mc(){return Minecraft.getInstance();}private static Path root(){return mc().gameDirectory.toPath();}
    private static void check(boolean value,String note){checks++;notes.add(note);if(!value)throw new AssertionError(note);}
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void click(String label){var s=mc().screen;var b=s.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));check(b.active,"Button enabled: "+label);s.mouseClicked(b.getX()+2,b.getY()+2,0);s.mouseReleased(b.getX()+2,b.getY()+2,0);}
    private static void open(String id){CompatibilityScreenOpen.open(new WorkspaceScreen(),id);check(mc().screen instanceof CompatibilityScreen,"Workbench opens: "+id);screen=(CompatibilityScreen)mc().screen;}
    private static boolean exists(String id){return mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static GenericCompatFixture.GenericRecipe nativeRecipe(String id){return (GenericCompatFixture.GenericRecipe)mc().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow().value();}
    private static JsonArray condition(String type,boolean enabled){var value=new JsonObject();value.addProperty("type",type);if(type.endsWith(":feature"))value.addProperty("enabled",enabled);var a=new JsonArray();a.add(value);return a;}
    private static void raw(JsonObject value)throws Exception{click("更多");click("编辑原始 JSON（原生格式）");((MultiLineEditBox)field(mc().screen,"input")).setValue(ProjectStore.JSON.toJson(value));click("检查并保存");}
    private static RecipeDraft store(String id,JsonObject data,String policy){var d=new RecipeDraft(id,data);d.origin="add";d.forceNative=true;d.loadConditionPolicy=policy;EditorRuntime.project().recipes.put(id,d);return d;}
    private static void prepare()throws Exception{
        check(!Files.exists(root().resolve("load-conditions-verification.json")),"Fresh single-session examination");EditorRuntime.project();SimpleRecipes.setEnabled(root(),true);EditorRuntime.invalidateRecipes();
        check(exists(LIVE)&&!exists(SOURCE),"Native true/false custom conditions govern original data loading");
        var loaded=EditorRuntime.recipes().stream().filter(r->r.id().equals(LIVE)).findFirst().orElseThrow();check(!loaded.capabilityLabel().equals("只读"),"Loading metadata no longer labels native recipe read-only");check(LoadConditions.present(loaded.data()),"Source loading envelope is retained for authoring");
        open(LIVE);check(LoadConditions.ignored(screen.draft)&&screen.draft.forceNative,"Force workbench defaults to ignore and native-only validation");
        var value=screen.draft.data.deepCopy();value.add(LoadConditions.KEY,condition(PREFIX+"feature",false));value.getAsJsonObject("settings").addProperty("work",88);value.getAsJsonObject("conditions").addProperty("work",17);raw(value);
        check(mc().screen==screen,"Raw native editor accepts valid payload despite false loading condition");check(screen.draft.data.getAsJsonObject("conditions").get("work").getAsInt()==17,"Bare machine conditions remain editable");check(LoadConditions.present(screen.draft.source),"Rollback source retains original condition metadata");check(!screen.dropTargets(new ItemStack(Items.COPPER_INGOT)).isEmpty(),"Conditional native recipe still exposes direct JEI drag targets");
        click("字段映射");var mapping=mc().screen;var d=(CompatDefinition)field(mapping,"definition");var wrong=d.fields.stream().filter(f->f.path.equals("/settings")).findFirst().orElse(null);if(wrong==null)wrong=d.add("/settings","不完整映射",CompatDefinition.Kind.ITEM,CompatDefinition.Role.INPUT);wrong.readOnlyReason="";click("确认并保存映射");check(mc().screen==screen,"Incomplete mapping cannot veto native-only confirmation");
        var before=screen.draft.data.deepCopy();var bad=before.deepCopy();bad.getAsJsonObject("product").addProperty("id",PREFIX+"missing_item");raw(bad);check(mc().screen instanceof CompatValueScreen,"Native invalid reference is reported before writing");check(screen.draft.data.equals(before),"Native edit failure restores the complete previous draft");mc().screen.onClose();
        var kept=before.deepCopy();kept.add(LoadConditions.KEY,condition("neoforge:false",false));kept.getAsJsonObject("product").addProperty("id",PREFIX+"missing_item");store(FALSE,kept,LoadConditions.PRESERVE);
        var positive=before.deepCopy();positive.add(LoadConditions.KEY,JsonParser.parseString("[{type:'neoforge:and',values:[{type:'neoforge:mod_loaded',modid:'minecraft'},{type:'neoforge:not',value:{type:'neoforge:tag_empty',tag:'minecraft:planks'}},{type:'kubejs_editor_verification:feature',enabled:true}]}]").getAsJsonArray());store(TRUE,positive,LoadConditions.PRESERVE);check(RecipeLoadConditions.matches(positive),"Nested native custom, mod and tag conditions use KJS context");
        var unknown=before.deepCopy();unknown.add(LoadConditions.KEY,condition("unregistered:condition",true));var u=store(UNKNOWN,unknown,LoadConditions.IGNORE);EditorRuntime.validateRecipe(u,Set.of());checks++;u.loadConditionPolicy=LoadConditions.PRESERVE;boolean rejected=false;try{EditorRuntime.validateRecipe(u,Set.of());}catch(RuntimeException error){rejected=error.getMessage().contains("加载条件解析失败");}check(rejected,"Unknown condition is diagnosed when preserved");u.loadConditionPolicy=LoadConditions.IGNORE;
        var source=JsonParser.parseString(Files.readString(root().resolve("kubejs/data/kubejs_editor_verification/recipe/conditional_source.json"))).getAsJsonObject();var forced=store(SOURCE,source,LoadConditions.IGNORE);forced.origin="existing";forced.operation="replace";forced.source=source.deepCopy();
        EditorRuntime.saveDraft();check(EditorRuntime.validationIssues().isEmpty(),"One batch accepts ignore, preserve true and preserve false without artificial veto");
        var disk=ProjectStore.read(root()).recipes.get(LIVE);check(disk.forceNative&&LoadConditions.ignored(disk)&&LoadConditions.present(disk.data),"Schema-2 persistence keeps force policy and original condition encoding");open(LIVE);mc().options.guiScale().set(2);mc().resizeDisplay();
    }
    private static void apply()throws Exception{var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);}
    private static void verifyFirst()throws Exception{
        check(result.success(),"Conditional batch applies: "+result.message());check(result.message().contains("加载条件未满足"),"Normal conditional absence is explained, not failed or rollback-required");
        check(nativeRecipe(LIVE).settings().work()==88&&nativeRecipe(LIVE).conditions().work()==17,"Ignored loading envelope does not discard real machine conditions");check(exists(SOURCE)&&exists(UNKNOWN)&&exists(TRUE)&&!exists(FALSE),"Actual server obeys each rule's override policy");check(!EditorRuntime.locked()&&EditorRuntime.state().activation==null,"False condition does not lock the editor transaction");
        open(FALSE);check(screen.draft.loadConditionPolicy.equals(LoadConditions.PRESERVE),"Conditionally absent saved rule can reopen with policy retained");click("更多");click("加载条件：保留原条件（点击切换）");check(LoadConditions.ignored(screen.draft),"Existing skipped rule can switch to force ignore");
        var fixed=screen.draft.data.deepCopy();fixed.getAsJsonObject("product").addProperty("id","minecraft:diamond");raw(fixed);check(mc().screen==screen,"Previously skipped invalid payload can be repaired and forced");
        var becameFalse=WorkspaceState.copy(EditorRuntime.state().applied.recipes.get(TRUE),RecipeDraft.class);becameFalse.data.add(LoadConditions.KEY,condition("neoforge:false",false));EditorRuntime.project().recipes.put(TRUE,becameFalse);EditorRuntime.saveDraft();apply();
    }
    private static void finish(Throwable error)throws Exception{report.addProperty("passed",error==null);report.addProperty("checks",checks);report.addProperty("gameLaunches",1);report.addProperty("phase",phase);report.add("checksDetail",notes);if(error!=null){var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.addProperty("error",text.toString());}Files.writeString(root().resolve("load-conditions-verification.json"),ProjectStore.JSON.toJson(report));phase=99;mc().stop();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!Boolean.getBoolean("kubejs.editor.verifyLoadConditions")||phase==99)return;ticks++;try{
        if(ticks>9000)throw new AssertionError("Timeout "+phase+" "+EditorRuntime.activationError);
        if(phase==0&&mc().screen instanceof TitleScreen&&ticks>80){mc().createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
        if(phase==1&&mc().level!=null&&mc().player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>160){prepare();phase=2;ticks=0;return;}
        if(phase==2&&ticks>20){Screenshot.grab(mc().gameDirectory,"load-conditions-scale2.png",mc().getMainRenderTarget(),c->{});mc().options.guiScale().set(3);mc().resizeDisplay();phase=3;ticks=0;return;}
        if(phase==3&&ticks>20){check(!screen.dropTargets(new ItemStack(Items.COPPER_INGOT)).isEmpty(),"GUI scale 3 retains JEI drag targets after override");Screenshot.grab(mc().gameDirectory,"load-conditions-scale3.png",mc().getMainRenderTarget(),c->{});apply();phase=4;ticks=0;return;}
        if(phase==4&&result!=null){verifyFirst();phase=5;ticks=0;return;}
        if(phase==5&&result!=null){check(result.success(),"Policy changes reload without spurious rollback: "+result.message());check(exists(FALSE)&&!exists(TRUE),"Second reload turns forced recipe on and preserved false recipe off");var p=store(PENDING,RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:stick'}],result:{id:'minecraft:paper'}}"),LoadConditions.IGNORE);EditorRuntime.saveDraft();result=null;EditorRuntime.revokeRecipe(SOURCE,r->result=r);phase=6;ticks=0;return;}
        if(phase==6&&result!=null){check(result.success()&&!exists(SOURCE),"Revoke restores conditional absence of the original false source");check(EditorRuntime.project().recipes.containsKey(PENDING),"Revoke preserves unrelated pending work");EditorRuntime.rollback();result=null;EditorRuntime.reloadResult(false,r->result=r);phase=7;ticks=0;return;}
        if(phase==7&&result!=null){check(result.success()&&exists(SOURCE),"Rollback restores the force override and actual native recipe");check(EditorRuntime.project().recipes.containsKey(PENDING),"Rollback preserves unrelated pending work");finish(null);}
    }catch(Throwable e){e.printStackTrace();try{finish(e);}catch(Exception write){write.printStackTrace();phase=99;mc().stop();}}}
    private ClientLoadConditionVerification(){}
}
