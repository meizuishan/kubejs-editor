package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Two real launches; real target serializers, JEI layouts and transaction lifecycle. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientSimpleRecipeVerification {
    private static final String AA="actuallyadditions:crushing/allium",POWAH="powah:energizing/niotic_crystal";
    private static int phase,ticks,checks,stage;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object field(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{var method=WorkspaceScreen.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(screen,args);}
    private static EditorRuntime.Loaded loaded(String id){return EditorRuntime.recipes().stream().filter(r->r.id().equals(id)).findFirst().orElseThrow(()->new AssertionError("Missing native recipe "+id));}
    private static void select(String id)throws Exception{call("selectLoaded",new Class[]{EditorRuntime.Loaded.class},loaded(id));}
    private static EditorProject.RecipeDraft draft()throws Exception{return (EditorProject.RecipeDraft)field(screen,"recipe");}
    @SuppressWarnings("unchecked") private static WorkspaceScreen.GhostTarget ghost(boolean output)throws Exception{
        var slots=(List<RecipeCanvas.Slot>)field(screen,"slots");for(int i=0;i<slots.size();i++)if(slots.get(i).output()==output)return screen.ghostTargets().get(i);throw new AssertionError("Missing ghost role "+output);
    }
    private static JsonObject nativeData(String id){return dev.kubejseditor.export.GenericFormatter.encodeRecipe(Minecraft.getInstance().getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).orElseThrow()).getAsJsonObject();}
    private static void click(String label){var button=Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow();check(button.active,"Button active: "+label);button.onPress();}
    @SuppressWarnings({"rawtypes","unchecked"}) private static IRecipeLayoutDrawable<?> layout(String id){
        var runtime=WorkspaceJeiPlugin.runtime;var manager=runtime.getRecipeManager();
        for(IRecipeCategory category:manager.createRecipeCategoryLookup().get().toList())for(Object recipe:(List<?>)manager.createRecipeLookup(category.getRecipeType()).get().toList()){
            var registry=recipe instanceof RecipeHolder<?> h?h.id():category.getRegistryName(recipe);
            if(registry==null&&Minecraft.getInstance().level.getRecipeManager().byKey(ResourceLocation.parse(id)).map(h->h.value()==recipe).orElse(false))registry=ResourceLocation.parse(id);
            if(!ResourceLocation.parse(id).equals(registry))continue;
            return (IRecipeLayoutDrawable<?>)manager.createRecipeLayoutDrawable(category,recipe,runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup()).orElseThrow();
        }throw new AssertionError("JEI has no native display for "+id);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void show(String id){var value=layout(id);WorkspaceJeiPlugin.runtime.getRecipesGui().showRecipes((IRecipeCategory)value.getRecipeCategory(),List.of(value.getRecipe()),List.of());}
    @SuppressWarnings("unchecked") private static void setParameter(String label,String value)throws Exception{
        for(Object form:(List<?>)field(screen,"form"))if(field(form,"label").equals(label)){((Consumer<String>)field(form,"set")).accept(value);return;}throw new AssertionError("Parameter missing: "+label);
    }
    private static void save()throws Exception{call("stage",new Class[]{});EditorRuntime.saveDraft();}
    private static void shot(String name){Screenshot.grab(Minecraft.getInstance().gameDirectory,"simple-"+name+".png",Minecraft.getInstance().getMainRenderTarget(),c->{});}
    private static void finish()throws Exception{
        var mods=net.neoforged.fml.ModList.get();var report=new JsonObject();report.addProperty("passed",true);report.addProperty("stage",stage);report.addProperty("checks",checks);
        for(String id:List.of("neoforge","jei","actuallyadditions","powah"))report.addProperty(id,mods.getModContainerById(id).orElseThrow().getModInfo().getVersion().toString());
        Files.writeString(root().resolve("simple-stage"+stage+".json"),ProjectStore.JSON.toJson(report));Minecraft.getInstance().stop();phase=99;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifySimple")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(ticks>2400)throw new AssertionError("Timeout phase "+phase);
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>80){stage=Files.exists(root().resolve("simple-stage1.json"))?2:1;mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>120){
                screen=new WorkspaceScreen();mc.setScreen(screen);
                if(stage==2){check(!SimpleRecipes.enabled(),"Disabled preference survives restart");check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unrelated"),"Unrelated pending operation survives restart");check(loaded(AA).error()==null&&loaded(POWAH).error()==null,"Persisted metadata restores authoring adapters with discovery off");check(nativeData(POWAH).get("energy").getAsLong()==123456789L,"Modified native energy survives actual restart");check(nativeData(AA).getAsJsonArray("result").get(0).getAsJsonObject().getAsJsonObject("result").get("count").getAsInt()==3,"Nested output survives actual restart");select(POWAH);check(draft().simple&&!(boolean)field(screen,"readOnly"),"Saved simple rule reopens for editing");phase=20;ticks=0;return;}
                check(!SimpleRecipes.enabled()&&loaded(AA).error()!=null,"Unknown native serializer starts read-only, discovery off");
                var top=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("强制兼容")).map(c->(Button)c).findFirst().orElseThrow();
                var add=mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().contains("新增功能")).map(c->(Button)c).findFirst().orElseThrow();
                check(top.getY()==add.getY()&&mc.screen.children().stream().filter(c->c instanceof Button b&&List.of("新增功能","修改现有","机械动力","已有修改").stream().anyMatch(t->b.getMessage().getString().contains(t))).allMatch(c->((Button)c).getX()<top.getX()),"Dedicated compatibility tab is last in top navigation");click("强制兼容");check((int)field(screen,"tab")==5,"Top tab opens dedicated compatibility page");click("选择配方");
                @SuppressWarnings("unchecked") var options=(List<ChoiceScreen.Choice>)field(mc.screen,"choices");var choice=options.stream().filter(c->c.text().contains(AA)).findFirst().orElseThrow();check(choice.text().contains("只读"),"Dedicated recipe chooser shows read-only capability state");choice.action().run();mc.setScreen(screen);click("尝试简单适配");phase=2;ticks=0;return;
            }
            if(phase<2||ticks<30)return;
            switch(phase){
                case 2 -> {
                    Files.writeString(root().resolve("simple-discovery.json"),ProjectStore.JSON.toJson(List.of(loaded(AA),loaded(POWAH))));
                    check(SimpleRecipes.enabled()&&draft().simple&&!(boolean)field(screen,"readOnly"),"Read-only action enables native simple editing: "+field(screen,"status"));
                    check(screen.ghostTargets().size()==2,"Nested AA output exposes two JEI ghost slots");var targets=screen.ghostTargets();var target=targets.getFirst();var area=target.area();String before=draft().data.toString();screen.mouseClicked(area.getX()+2,area.getY()+2,1);check(draft().data.toString().equals(before),"Right-click keeps fixed slot, no exception or mutation");
                    target.accept().accept(new ItemStack(Items.STONE));targets.getLast().accept().accept(new ItemStack(Items.DIAMOND,3));save();
                    check(draft().data.getAsJsonArray("result").get(0).getAsJsonObject().getAsJsonObject("result").get("id").getAsString().equals("minecraft:diamond"),"JEI ghost output keeps native result wrapper");
                    check(loaded("kubejs_editor:conditional").error()!=null&&loaded("kubejs_editor:conditional").data().has("neoforge:conditions"),"Original load conditions recovered from resource and kept read-only");
                    check(loaded("kubejs_editor:alternative").error()!=null,"Native OR Ingredient remains read-only");check(loaded("kubejs_editor:runtime_simple").error()==null,"Runtime native recipe is eligible when its serializer supports it");
                    select("kubejs_editor:conditional");check((boolean)field(screen,"readOnly")&&((List<?>)field(screen,"slots")).isEmpty(),"Conditional native recipe opens a read-only diagnostic without attempting editable slots");select("kubejs_editor:alternative");check((boolean)field(screen,"readOnly"),"OR recipe opens for read-only inspection without layout failure");
                    var layout=layout(AA);var source=JeiRecipeSources.resolve(layout);check(source.id()!=null&&source.id().toString().equals(AA),"JEI resolves authoritative actual recipe ID");JeiRecipeSources.capture(layout,source.id());check(SimpleRecipes.boundary(AA,loaded(AA).data())==null,"Filled JEI input/output slots match native mapping");
                    SimpleRecipes.record(AA,new SimpleRecipes.JeiEvidence("test:mismatch","test",2,2,0,0));EditorRuntime.invalidateRecipes();check(loaded(AA).error()!=null,"Conflicting JEI roles force read-only");JeiRecipeSources.capture(layout,source.id());EditorRuntime.invalidateRecipes();
                    select(POWAH);check(draft().simple&&SimpleRecipeAdapter.parameters(draft().data).contains("energy"),"Powah native energy field exposed");
                }
                case 3 -> {
                    setParameter("参数 /energy","123456789");var input=ghost(false);var output=ghost(true);input.accept().accept(new ItemStack(Items.IRON_INGOT));output.accept().accept(new ItemStack(Items.EMERALD,2));save();
                    check(draft().data.get("energy").getAsLong()==123456789L,"Numeric UI writer preserves native integral energy");EditorRuntime.validateRecipe(draft().data,Set.of());check(EditorRuntime.validationIssues().isEmpty(),"Both native serializers validate edits");
                    var roundtrip=draft().data.deepCopy();roundtrip.getAsJsonObject("result").addProperty("count",2147483648L);boolean refused=false;try{EditorRuntime.validateRecipe(roundtrip,Set.of());}catch(RuntimeException expected){refused=true;}check(refused,"Out-of-range native ItemStack count rejected");
                }
                case 4 -> {shot("powah");mc.options.guiScale().set(3);mc.resizeDisplay();}
                case 5 -> {check(screen.ghostTargets().size()==2&&WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"Simple canvas and JEI still active at GUI scale 3");shot("scale3");var review=EditorRuntime.review();check(!review.restartRequired(),"Machine edits need data reload only");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 6 -> {if(result==null)return;check(result.success(),"Machine rules applied and actual native reload verified: "+result.message());check(EditorRuntime.project().recipes.isEmpty()&&EditorRuntime.state().applied.recipes.size()==2,"Only submitted two overrides promoted");check(nativeData(POWAH).get("energy").getAsLong()==123456789L,"Native RecipeManager contains modified Powah energy");var powah=nativeData(POWAH);check(powah.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:iron_ingot")&&powah.getAsJsonObject("result").get("id").getAsString().equals("minecraft:emerald")&&powah.getAsJsonObject("result").get("count").getAsInt()==2,"Native input/output roles and quantity match ghost targets regardless of JSON field order");check(nativeData(AA).getAsJsonArray("result").get(0).getAsJsonObject().getAsJsonObject("result").get("id").getAsString().equals("minecraft:diamond"),"Native RecipeManager contains nested AA output");result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 7 -> {if(result==null)return;check(result.success(),"Repeated native reload keeps rules valid");show(AA);}
                case 8 -> {
                    var layouts=field(mc.screen,"layouts");var list=(List<?>)field(layouts,"recipeLayoutsWithButtons");var buttons=(List<?>)field(list.getFirst(),"buttons");mezz.jei.api.gui.buttons.IIconButtonController edit=null;
                    for(Object b:buttons){var controller=(mezz.jei.api.gui.buttons.IIconButtonController)field(b,"controller");if(controller.getClass().getName().contains("WorkspaceJeiPlugin"))edit=controller;}check(edit!=null,"Actual machine JEI layout has editor side button");
                    edit.onPress(new mezz.jei.api.gui.inputs.IJeiUserInput(){public com.mojang.blaze3d.platform.InputConstants.Key getKey(){return com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);}public int getModifiers(){return 0;}public boolean isSimulate(){return false;}public boolean is(net.minecraft.client.KeyMapping key){return false;}public boolean is(mezz.jei.api.runtime.IJeiKeyMapping key){return false;}});
                }
                case 9 -> {check(mc.screen instanceof WorkspaceScreen,"Machine JEI button opens editor");screen=(WorkspaceScreen)mc.screen;check(draft().id.equals(AA)&&draft().simple&&!(boolean)field(screen,"readOnly"),"Machine JEI button opens exact native ID with mapping evidence");SimpleRecipes.setEnabled(EditorRuntime.root(),false);var unrelated=new EditorProject.RecipeDraft("kubejs_editor:unrelated",RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'minecraft:stick'}],result:{id:'minecraft:paper'}}"));EditorRuntime.project().recipes.put(unrelated.id,unrelated);EditorRuntime.saveDraft();finish();return;}
                case 20 -> {result=null;EditorRuntime.revokeRecipe(AA,r->result=r);}
                case 21 -> {if(result==null)return;check(result.success()&&nativeData(AA).getAsJsonArray("result").get(0).getAsJsonObject().getAsJsonObject("result").get("id").getAsString().equals("minecraft:magenta_dye"),"Revoking AA override restores native source");check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unrelated"),"Revoke leaves unrelated pending work unsubmitted");result=null;EditorRuntime.revokeRecipe(POWAH,r->result=r);}
                case 22 -> {if(result==null)return;check(result.success()&&nativeData(POWAH).get("energy").getAsLong()==300000L,"Revoking Powah restores native energy");check(EditorRuntime.state().applied.recipes.isEmpty(),"Both simple overrides revoked");EditorRuntime.rollback();result=null;EditorRuntime.reloadResult(false,r->result=r);}
                case 23 -> {if(result==null)return;check(result.success()&&nativeData(POWAH).get("energy").getAsLong()==123456789L,"Rollback restores rule and actual machine data");check(EditorRuntime.project().recipes.containsKey("kubejs_editor:unrelated")&&EditorRuntime.state().applied.recipes.get(POWAH).simple,"Rollback keeps metadata and unrelated draft");finish();return;}
            }
            phase++;ticks=0;
        }catch(Throwable error){try{Files.writeString(root().resolve("simple-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(error.toString())+"}");}catch(Exception ignored){}error.printStackTrace();mc.stop();phase=99;}
    }
}
