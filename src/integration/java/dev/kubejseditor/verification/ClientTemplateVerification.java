package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.recipe.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientTemplateVerification {
    private static int phase,ticks,checks;private static WorkspaceScreen screen;private static EditorRuntime.ReloadResult result;private static String id;private static java.util.concurrent.CompletableFuture<Void> languageReload;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static Object call(String name,Class<?>[] types,Object... args)throws Exception{var m=WorkspaceScreen.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(screen,args);}
    private static JsonObject data()throws Exception{var f=WorkspaceScreen.class.getDeclaredField("recipe");f.setAccessible(true);return ((dev.kubejseditor.model.EditorProject.RecipeDraft)f.get(screen)).data;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyTemplates")||phase==99)return;var mc=Minecraft.getInstance();ticks++;
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>100){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>120){
                screen=new WorkspaceScreen();mc.setScreen(screen);
                var template=RecipeTemplates.get("createaddition:rolling");check(template!=null,"Shared JSON template loads from instance directory");
                check(EditorCompatibility.requirement("createaddition","[1.6.0,1.7)").isEmpty(),"Real addon version range is satisfied");
                check(!EditorCompatibility.requirement("createaddition","[2,3)").isEmpty(),"Wrong addon version is diagnosed");
                call("createRecipe",new Class[]{RecipeAdapter.class},template);mc.setScreen(screen);phase=2;ticks=0;return;
            }
            if(phase<2||ticks<30)return;if(ticks>1800)throw new AssertionError("Timeout phase "+phase);
            switch(phase){
                case 2 -> {
                    check(screen.ghostTargets().size()==2,"New template has two refillable ghost slots");
                    var slots=RecipeCanvas.slots(data());slots.getFirst().set(RecipeAdapters.parse("{item:'minecraft:iron_ingot'}"));slots.getLast().set(RecipeAdapters.parse("{id:'minecraft:iron_nugget',count:2}"));
                    var tags=SlotChoices.tags(slots.getFirst(),tag->{});check(tags.stream().anyMatch(c->c.text().equals("#c:ingots/iron")),"Live iron tags are offered without typing IDs");
                    check(SlotChoices.resources(slots.getFirst(),obj->{}).stream().anyMatch(c->c.text().contains("minecraft:iron_ingot")),"Search includes live item names and IDs");
                    var fluidSlot=RecipeCanvas.slots(RecipeAdapters.get("create:filling").newRecipe()).stream().filter(RecipeCanvas.Slot::fluid).findFirst().orElseThrow();
                    check(SlotChoices.resources(fluidSlot,obj->{}).stream().anyMatch(c->c.text().contains("minecraft:water")),"Fluid search includes water");
                    data().add("editor_example",RecipeAdapters.parse("{nested:[1,2],keep:'original'}"));
                    data().add("neoforge:conditions",JsonParser.parseString("[{type:'neoforge:mod_loaded',modid:'createaddition'}]"));
                    check(EditorRuntime.importBoundary(data())==null,"Recipe conditions do not become opaque ingredients");
                    check(!(boolean)call("canAdd",new Class[]{boolean.class,boolean.class},true,false),"Template output limit controls the real add button");
                    call("changed",new Class[]{});call("stage",new Class[]{});id=EditorRuntime.project().recipes.keySet().stream().filter(k->k.startsWith("kubejs_editor:")).findFirst().orElseThrow();
                    data().getAsJsonArray("results").add(RecipeAdapters.parse("{id:'minecraft:gold_nugget'}"));call("changed",new Class[]{});call("stage",new Class[]{});
                    var issue=EditorRuntime.validationIssues().stream().filter(i->i.key().equals("recipe:"+id)).findFirst().orElseThrow();
                    check(issue.pointer().equals("/results"),"Over-limit error carries an output field pointer");call("focusIssue",new Class[]{ValidationIssue.class},issue);
                    var selection=WorkspaceScreen.class.getDeclaredField("selectedSlot");selection.setAccessible(true);check(((RecipeCanvas.Slot)selection.get(screen)).label().endsWith("2"),"Clicking error selects the excess output slot");
                    data().getAsJsonArray("results").remove(1);call("changed",new Class[]{});call("stage",new Class[]{});
                    check(EditorRuntime.validationIssues().isEmpty(),"Template passes actual serializer codec and references");
                    check(ProjectStore.read(root()).recipes.get(id).data.has("editor_example"),"Template draft survives disk round trip");
                    mc.options.guiScale().set(3);mc.resizeDisplay();
                }
                case 3 -> {
                    check(screen.ghostTargets().size()==2,"Template ghost slots survive GUI scaling");
                    Screenshot.grab(mc.gameDirectory,"template-rolling.png",mc.getMainRenderTarget(),c->{});
                    var review=EditorRuntime.review();check(!review.restartRequired(),"Template recipe does not trigger unrelated restart");
                    EditorRuntime.apply(review);result=null;EditorRuntime.reloadResult(false,r->result=r);
                }
                case 4 -> {
                    if(result==null)return;check(result.success(),"Template-generated KJS applies and reloads");
                    var actual=mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id));check(actual.isPresent(),"Template recipe exists in native manager");
                    check(EditorRuntime.project().recipes.isEmpty(),"Successful native activation clears pending queue");
                    check(EditorRuntime.state().applied.recipes.get(id).data.has("editor_example"),"Applied template keeps unknown fields");
                    check(Files.readString(root().resolve(WorkspaceState.GENERATED.getFirst())).contains("editor_example"),"Generated standard KJS keeps unknown fields");
                    check(EditorRuntime.recipes().stream().anyMatch(r->r.id().equals(id)&&r.error()==null),"Template recipe imports into forms by exact ID");
                    result=null;EditorRuntime.revokeRecipe(id,r->result=r);
                }
                case 5 -> {
                    if(result==null)return;check(result.success(),"Template recipe can be revoked independently");
                    check(!EditorRuntime.state().applied.recipes.containsKey(id)&&EditorRuntime.project().recipes.isEmpty(),"Revocation leaves no phantom pending rule");
                    check(mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id)).isEmpty(),"Revocation removes only added recipe");
                    var preferences=new PanelPreferences();preferences.left=.25;preferences.right=.28;preferences.save(root());var restored=PanelPreferences.read(root());check(restored.left==.25&&restored.right==.28,"Panel widths persist independently of gameplay drafts");
                    mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");languageReload=mc.reloadResourcePacks();
                }
                case 6 -> {
                    if(!languageReload.isDone())return;languageReload.join();screen=new WorkspaceScreen();mc.setScreen(screen);
                    check(UiText.text("一键应用").equals("Apply all"),"English UI uses language resources");
                    check(mc.screen.children().stream().anyMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith("Add new")),"English navigation renders after resource reload");
                }
                case 7 -> {
                    if(mc.getOverlay()!=null)return;
                    check(WorkspaceJeiPlugin.runtime.getIngredientListOverlay().isListDisplayed(),"JEI remains visible with resized English workspace");Screenshot.grab(mc.gameDirectory,"template-english.png",mc.getMainRenderTarget(),c->{});
                    Files.writeString(root().resolve("template-result.json"),"{\"passed\":true,\"checks\":"+checks+"}");mc.stop();phase=99;return;
                }
            }
            phase++;ticks=0;
        }catch(Throwable e){try{Files.writeString(root().resolve("template-failure.json"),"{\"phase\":"+phase+",\"error\":"+ProjectStore.JSON.toJson(e.toString())+"}");}catch(Exception ignored){}e.printStackTrace();mc.stop();phase=99;}
    }
}
