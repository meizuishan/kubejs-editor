package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.model.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientVintageVerification {
    private static int phase,ticks,checks,index;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;private static String replacedId;private static JsonObject original;
    private static final List<VintageProcessing.Mode> modes=List.copyOf(VintageProcessing.all());
    private static final Map<String,String> switched=new HashMap<>();
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{var m=WorkspaceScreen.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}
    private static String id(VintageProcessing.Mode mode){return "vintage_test:"+mode.path();}
    private static EditorProject.RecipeDraft draft(VintageProcessing.Mode mode){return EditorRuntime.project().recipes.get(id(mode));}
    private static net.minecraft.client.gui.components.Button button(String text){return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(text)).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();}
    private static void verifyCreationMenu()throws Exception{
        call("newRecipe",new Class[]{});var field=ChoiceScreen.class.getDeclaredField("choices");field.setAccessible(true);
        @SuppressWarnings("unchecked") var choices=(List<ChoiceScreen.Choice>)field.get(Minecraft.getInstance().screen);
        check(choices.stream().filter(c->c.text().equals("复古 · 真空腔室")).count()==1,"Chamber has one workstation entry");
        check(choices.stream().filter(c->c.text().equals("复古 · 弯曲压力机")).count()==1,"Curving heads share one workstation entry");
        check(choices.stream().noneMatch(c->c.text().equals("复古 · 真空处理")||c.text().equals("复古 · 加压处理")||c.text().matches("复古 · .*弯曲")),"Individual states are not duplicated in new-recipe choices");
        check(choices.stream().anyMatch(c->c.text().equals("复古 · 真空腔室")&&c.searchText().contains("加压处理")),"Old process names remain searchable");
        Minecraft.getInstance().screen.onClose();
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"vintage-"+name+".png",mc.getMainRenderTarget(),c->{});}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyVintage")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                check(BuiltInRegistries.ITEM.get(ResourceLocation.parse(VintageProcessing.PREFIX+"belt_grinder")).getDescription().getString().equals("砂带研磨机"),"Original pack Chinese patch is loaded by KubeJS assets");
                check(modes.size()==13,"Thirteen fixed processing modes");
                EditorRuntime.cancel(new PendingOperation("recipe","compat:dragons","配方生成"));
                for(var mode:modes){
                    check(EditorCompatibility.hasRecipeType(mode.type()),"Native serializer registered: "+mode.path());JsonObject data=RecipeAdapters.get(mode.type()).newRecipe();
                    data.getAsJsonArray("results").get(0).getAsJsonObject().addProperty("count",2);
                    if(mode.path().equals("polishing")){data.addProperty("speedLimits",3);data.addProperty("fragile",true);}
                    if(mode.path().startsWith("curving_")){data.addProperty("requiredBonks",5);data.addProperty("headDamage",4);}
                    if(mode.path().equals("hammering")){data.addProperty("hammer_blows",6);data.addProperty("anvil_block","minecraft:iron_block");}
                    if(VintageProcessing.basin(mode.type())){
                        data.getAsJsonArray("ingredients").add(RecipeAdapters.parse("{type:'neoforge:single',fluid:'minecraft:water',amount:250}"));
                        data.getAsJsonArray("ingredients").add(RecipeAdapters.parse("{type:'neoforge:single',fluid:'minecraft:lava',amount:250}"));
                        data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:water',amount:100}"));
                        data.getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:lava',amount:100}"));
                    }
                    EditorRuntime.validateRecipe(data,Set.of());checks++;EditorRuntime.project().recipes.put(id(mode),new EditorProject.RecipeDraft(id(mode),data));
                }
                var seq=new EditorProject.RecipeDraft("vintage_test:assembly",RecipeAdapters.get("create:sequenced_assembly").newRecipe());seq.data.add("sequence",new JsonArray());for(String type:VintageProcessing.assemblyTypes())RecipeCanvas.addStep(seq.data,type);EditorRuntime.validateRecipe(seq.data,Set.of());EditorRuntime.project().recipes.put(seq.id,seq);
                EditorRuntime.saveDraft();screen=new WorkspaceScreen();mc.setScreen(screen);verifyCreationMenu();call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},draft(modes.getFirst()),false);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<30)return;if(ticks>1800)throw new AssertionError("Timeout phase "+phase);
            switch(phase){
                case 2 -> {
                    var mode=modes.get(index);check(!screen.ghostTargets().isEmpty(),"Ghost slots render: "+mode.path());check(VintageSceneRenderer.failures().isEmpty(),"Native device animation: "+mode.path());
                    var station=VintageProcessing.workstation(mode.type());if(station!=null){
                        JsonObject before=draft(mode).data.deepCopy();int count=EditorRuntime.project().recipes.size();
                        button(VintageProcessing.stateLabel(mode.type())).onPress();var after=draft(mode);String state=after.data.get("type").getAsString();switched.put(after.id,state);
                        check(!state.equals(mode.type())&&station.types().contains(state),"Right-side control switches native state: "+mode.path());
                        var expected=before.deepCopy();expected.addProperty("type",state);check(after.data.equals(expected),"State switch retains all materials, fluids, outputs, parameters and unknown fields");
                        check(after.id.equals(id(mode))&&EditorRuntime.project().recipes.size()==count,"State switch keeps ID and a single pending record");
                        var saved=ProjectStore.read(root()).recipes.get(id(mode));check(saved.data.equals(expected),"Changed state persists to disk");
                        screen=new WorkspaceScreen();mc.setScreen(screen);call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},saved,false);
                        check(button(VintageProcessing.stateLabel(state)).active,"Reopened draft shows correct state");
                    }
                    for(var t:screen.ghostTargets())check(t.area().getWidth()>0&&t.area().getHeight()>0,"Visible scaled target: "+mode.path());
                    if(Set.of("polishing","hammering","centrifugation","curving_w_shaped").contains(mode.path()))shot(mode.path());
                    index++;if(index<modes.size()){if(index==6){mc.options.guiScale().set(3);mc.resizeDisplay();}call("selectRecipe",new Class[]{EditorProject.RecipeDraft.class,boolean.class},draft(modes.get(index)),false);ticks=0;return;}
                    check(EditorRuntime.validationIssues().isEmpty(),"All modes and assembly validate together");var review=EditorRuntime.review();check(!review.restartRequired(),"Recipe-only batch needs no restart");EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                }
                case 3 -> {
                    if(result==null)return;check(result.success(),"Thirteen native recipes and assembly load through generated KJS");check(EditorRuntime.project().recipes.isEmpty(),"Successful batch clears pending changes");
                    for(var mode:modes){var recipe=mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id(mode))).orElseThrow().value();check(recipe!=null,"Actual recipe exists: "+mode.path());
                        var encoded=EditorRuntime.recipes().stream().filter(r->r.id().equals(id(mode))).findFirst().orElseThrow();check(encoded.error()==null,"Actual recipe imports: "+mode.path());
                        if(switched.containsKey(id(mode))){check(encoded.data().get("type").getAsString().equals(switched.get(id(mode))),"Changed serializer loads under original ID");call("selectLoaded",new Class[]{EditorRuntime.Loaded.class},encoded);check(button(VintageProcessing.stateLabel(switched.get(id(mode)))).active,"Imported recipe shows loaded device state");}
                        if(mode.path().startsWith("curving_"))check(encoded.data().get("requiredBonks").getAsInt()==5&&encoded.data().get("headDamage").getAsInt()==4,"Curving fields survive actual KJS and codec");
                        if(mode.path().equals("hammering"))check(encoded.data().get("hammer_blows").getAsInt()==6&&encoded.data().get("anvil_block").getAsString().equals("minecraft:iron_block"),"Hammer parameters survive actual KJS and codec");
                        if(mode.path().equals("polishing"))check(encoded.data().get("speedLimits").getAsInt()==3&&encoded.data().get("fragile").getAsBoolean(),"Grinding fields survive actual KJS and codec");
                        if(VintageProcessing.basin(mode.type()))check(RecipeLimits.forType(mode.type()).count(encoded.data(),false,true)==2&&RecipeLimits.forType(mode.type()).count(encoded.data(),true,true)==2,"Both fluid entries survive: "+mode.path());
                    }
                    call("selectLoaded",new Class[]{EditorRuntime.Loaded.class},EditorRuntime.recipes().stream().filter(r->r.id().equals("vintage_test:assembly")).findFirst().orElseThrow());
                }
                case 4 -> {
                    check(VintageSceneRenderer.failures().isEmpty(),"Vintage assembly step animations render");shot("assembly");
                    call("vintageDynamic",new Class[]{String.class},VintageProcessing.PREFIX+"auto_upgrade");check(mc.screen instanceof ChoiceScreen,"Automatic upgrading opens real smithing choices");mc.screen.onClose();
                    call("vintageDynamic",new Class[]{String.class},VintageProcessing.PREFIX+"leaves_vibrating");check(mc.screen instanceof ChoiceScreen,"Leaf processing opens mechanism/config choices");mc.screen.onClose();
                    String target=id(modes.getFirst());result=null;EditorRuntime.revokeRecipe(target,r->result=r);
                }
                case 5 -> {
                    if(result==null)return;check(result.success(),"Revoke Vintage added recipe immediately");check(mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id(modes.getFirst()))).isEmpty()&&EditorRuntime.project().recipes.isEmpty(),"Revocation leaves no new disable operation");
                    check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI remains active");
                    var loaded=EditorRuntime.recipes().stream().filter(r->r.id().startsWith(VintageProcessing.PREFIX)&&r.type().endsWith(":coiling")&&r.error()==null).findFirst().orElseThrow();replacedId=loaded.id();original=loaded.data().deepCopy();var change=new EditorProject.RecipeDraft(replacedId,loaded.data());change.operation="replace";change.origin="existing";change.data.getAsJsonArray("results").set(0,RecipeAdapters.parse("{id:'minecraft:gold_nugget',count:7}"));EditorRuntime.project().recipes.put(replacedId,change);EditorRuntime.saveDraft();var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                }
                case 6 -> {
                    if(result==null)return;check(result.success(),"Existing Vintage recipe replacement reloads");var loaded=EditorRuntime.recipes().stream().filter(r->r.id().equals(replacedId)).findFirst().orElseThrow();check(loaded.data().getAsJsonArray("results").get(0).getAsJsonObject().get("count").getAsInt()==7,"Existing recipe replacement is actually loaded");
                    var disabled=new EditorProject.RecipeDraft(replacedId,loaded.data());disabled.operation="remove";disabled.origin="existing";EditorRuntime.project().recipes.put(replacedId,disabled);EditorRuntime.saveDraft();var review=EditorRuntime.review();EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                }
                case 7 -> {
                    if(result==null)return;check(result.success()&&mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(replacedId)).isEmpty(),"Disabled existing Vintage recipe disappears");result=null;EditorRuntime.revokeRecipe(replacedId,r->result=r);
                }
                case 8 -> {
                    if(result==null)return;check(result.success(),"Revoke all editor overrides of the original recipe");var restored=EditorRuntime.recipes().stream().filter(r->r.id().equals(replacedId)).findFirst().orElseThrow();check(restored.data().equals(original)&&EditorRuntime.project().recipes.isEmpty(),"Restore original Vintage recipe without phantom disable operations");
                    Files.writeString(root().resolve("vintage-result.json"),"{\"passed\":true,\"checks\":"+checks+",\"fixedTypes\":13,\"dynamicEntrypoints\":3,\"assemblyTypes\":3}");mc.stop();phase=99;return;
                }
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("vintage-failure.json"),"{\"phase\":"+phase+",\"typeIndex\":"+index+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
