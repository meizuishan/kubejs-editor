package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.client.*;
import dev.kubejseditor.recipe.SimpleRecipes;
import dev.kubejseditor.recipe.compat.*;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.*;

/** Single isolated examination of the existing native recipe, without a custom machine recipe. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientChanceMappingVerification {
    private static int phase,ticks;
    private static final JsonObject report=new JsonObject();
    private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void click(String label){var mc=Minecraft.getInstance();var s=mc.screen;var b=s.children().stream().filter(c->c instanceof Button v&&v.getMessage().getString().equals(label)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("Missing button "+label));if(!b.active)throw new AssertionError("Disabled "+label);s.mouseClicked(b.getX()+2,b.getY()+2,0);s.mouseReleased(b.getX()+2,b.getY()+2,0);}
    private static void examine()throws Exception{
        var mc=Minecraft.getInstance();EditorRuntime.project();SimpleRecipes.setEnabled(mc.gameDirectory.toPath(),true);EditorRuntime.invalidateRecipes();
        var loaded=EditorRuntime.recipes().stream().filter(r->r.type().equals("actuallyadditions:crushing")&&r.data()!=null&&r.data().toString().contains("0.5")).findFirst().orElseThrow();
        report.addProperty("recipe",loaded.id());report.addProperty("indexError",loaded.error());report.add("source",loaded.data().deepCopy());
        CompatibilityScreenOpen.open(new WorkspaceScreen(),loaded.id());var screen=(CompatibilityScreen)mc.screen;
        var presentation=(RecipePresentation)field(screen,"presentation");report.add("automaticDefinition",ProjectStore.JSON.toJsonTree(presentation.definition));
        click("字段映射");var mapping=mc.screen;
        var definition=(CompatDefinition)field(mapping,"definition");String path="";for(int i=0;i<screen.draft.data.getAsJsonArray("result").size();i++){String candidate="/result/"+i+"/chance";var v=CompatJson.get(screen.draft.data,candidate);if(v.isJsonPrimitive()&&v.getAsDouble()==0.5){path=candidate;break;}}
        if(path.isEmpty())throw new AssertionError("No existing chance 0.5");
        var f=new CompatDefinition.Field(path,path,CompatDefinition.Kind.JSON,CompatDefinition.Role.PARAMETER);
        var roles=mapping.getClass().getDeclaredMethod("basicRoles",CompatDefinition.Field.class,JsonElement.class);roles.setAccessible(true);roles.invoke(mapping,f,CompatJson.get(screen.draft.data,path));click("数值修改");
        click("确认并保存映射");boolean confirmed=mc.screen==screen;report.addProperty("confirmed",confirmed);report.add("confirmedDefinition",ProjectStore.JSON.toJsonTree(definition));
        if(!confirmed){report.addProperty("confirmationStatus",String.valueOf(field(mapping,"status")));return;}
        EditorRuntime.validateRecipe(screen.draft.data,Set.of());report.addProperty("nativeRoundTrip",true);
        report.addProperty("mappingFileExists",Files.exists(mc.gameDirectory.toPath().resolve("kubejs/config/kubejs_editor/recipe-mappings.json")));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){if(!Boolean.getBoolean("kubejs.editor.verifyChanceMapping")||phase==99)return;var mc=Minecraft.getInstance();ticks++;try{
        if(ticks>5000)throw new AssertionError("Timeout");
        if(phase==0&&mc.screen instanceof TitleScreen&&ticks>80){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
        if(phase==1&&mc.level!=null&&mc.player!=null&&WorkspaceJeiPlugin.runtime!=null&&ticks>160){examine();finish(null);}
    }catch(Throwable e){e.printStackTrace();try{finish(e);}catch(Exception write){write.printStackTrace();phase=99;mc.stop();}}}
    private static void finish(Throwable error)throws Exception{report.addProperty("gameLaunches",1);report.addProperty("passed",error==null&&report.has("confirmed")&&report.get("confirmed").getAsBoolean());if(error!=null){var text=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(text));report.addProperty("error",text.toString());}Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("chance-mapping-verification.json"),ProjectStore.JSON.toJson(report));phase=99;Minecraft.getInstance().stop();}
}
