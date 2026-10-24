package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.runtime.EditorDiagnostics;
import dev.kubejseditor.runtime.EditorRuntime;
import dev.kubejseditor.storage.ProjectStore;
import dev.kubejseditor.storage.WorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end revocation of the real copied Skyhive editor rules. It intentionally uses the
 * production-shaped state (one added recipe and one replacement) without copying a player
 * save or account. The pack's own failing datapack-sync listener exercises compensation.
 */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientPackRevokeVerification {
    private static int phase,ticks,checks,index;
    private static final List<String> ids=new ArrayList<>();
    private static final JsonArray outcomes=new JsonArray();
    private static String current;
    private static boolean currentAdded;
    private static boolean announced;
    private static long startedAt;
    private static EditorRuntime.ReloadResult result;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    private static boolean serverExists(String id){var server=Minecraft.getInstance().getSingleplayerServer();return server!=null&&server.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static boolean clientExists(String id){var level=Minecraft.getInstance().level;return level!=null&&level.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static void report(boolean passed,Throwable error)throws Exception{
        var report=new JsonObject();report.addProperty("passed",passed);report.addProperty("checks",checks);report.add("revoked",new JsonArray());
        for(String id:ids)report.getAsJsonArray("revoked").add(id);
        report.add("outcomes",outcomes);report.addProperty("diagnostic",EditorDiagnostics.path(root()));
        if(error!=null)report.addProperty("error",String.valueOf(error));
        Files.writeString(root().resolve("pack-revoke.json"),ProjectStore.JSON.toJson(report));
    }
    private static RuntimeException fail(Throwable error){
        try{report(false,error);}catch(Exception ignored){}
        Minecraft.getInstance().stop();phase=99;
        if(error instanceof RuntimeException runtime)return runtime;
        return new RuntimeException(error);
    }
    private static void startNext()throws Exception{
        current=ids.get(index);var old=EditorRuntime.state().applied.recipes.get(current);
        currentAdded=old!=null&&("add".equals(old.origin)||(old.origin==null&&"add".equals(old.operation)));
        result=null;EditorRuntime.revokeRecipe(current,r->result=r);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyPackRevoke")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;
        if(startedAt==0)startedAt=System.currentTimeMillis();
        if(!announced){announced=true;System.out.println("[KubeJS Editor Verification] pack revoke verifier loaded; screen="+(mc.screen==null?"null":mc.screen.getClass().getName()));}
        if(System.currentTimeMillis()-startedAt>15*60*1000)throw fail(new AssertionError("超时，阶段 "+phase+" · "+EditorRuntime.activationError));
        try{
            String screen=mc.screen==null?"":mc.screen.getClass().getName();
            boolean titleReady=mc.screen!=null&&!screen.contains("LoadingOverlay")&&!screen.contains("GenericMessage");
            boolean fallback=startedAt>0&&System.currentTimeMillis()-startedAt>120_000;
            if(phase==0&&mc.level==null&&(titleReady||fallback)){System.out.println("[KubeJS Editor Verification] opening EditorVerification world; screen="+screen+", fallback="+fallback);mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&mc.getSingleplayerServer()!=null&&!mc.getSingleplayerServer().getRecipeManager().getRecipes().isEmpty()&&ticks>200){
                System.out.println("[KubeJS Editor Verification] world ready; applied="+EditorRuntime.state().applied.recipes.keySet());
                var applied=EditorRuntime.state().applied.recipes;
                if(applied.isEmpty())throw fail(new AssertionError("隔离副本没有已应用规则；请复制 Skyhive 的 kubejs/config/kubejs_editor 和生成脚本后重试"));
                ids.clear();ids.addAll(applied.keySet());
                check(applied.values().stream().anyMatch(r->"add".equals(r.origin)||"add".equals(r.operation)),"副本包含新增规则");
                check(applied.values().stream().anyMatch(r->"replace".equals(r.operation)||"remove".equals(r.operation)),"副本包含替换或禁用规则");
                index=0;phase=2;ticks=0;startNext();return;
            }
            if(phase==2){
                if(result==null||EditorRuntime.busy())return;
                var outcome=new JsonObject();outcome.addProperty("id",current);outcome.addProperty("added",currentAdded);
                outcome.addProperty("success",result.success());outcome.addProperty("message",result.message());
                outcome.addProperty("serverPresent",serverExists(current));outcome.addProperty("clientPresent",clientExists(current));
                outcomes.add(outcome);
                check(result.success(),"撤销成功："+current+" · "+result.message());
                if(currentAdded)check(!serverExists(current),"新增规则撤销后从服务端消失");
                else check(serverExists(current),"替换/禁用规则撤销后来源配方恢复");
                check(EditorRuntime.state().applied.recipes.get(current)==null,"撤销后记录从已有修改移除");
                phase=3;ticks=0;return;
            }
            if(phase==3){
                if(ticks<30)return;
                if(currentAdded)check(!clientExists(current),"客户端补偿后新增配方消失");
                else check(clientExists(current),"客户端补偿后来源配方恢复");
                index++;ticks=0;
                if(index<ids.size()){phase=2;startNext();return;}
                check(EditorRuntime.state().applied.recipes.isEmpty(),"所有规则撤销后工作区为空");
                check(!Files.exists(root().resolve(EditorRuntime.state().outputPath(WorkspaceState.GENERATED.getFirst()))),"最后一条规则撤销后生成脚本删除");
                check(EditorRuntime.project().recipes.isEmpty(),"撤销未产生额外待办");
                var diagnostic=JsonParser.parseString(EditorDiagnostics.read(root())).getAsJsonObject();
                var report=new JsonObject();report.addProperty("passed",true);report.addProperty("checks",checks);report.add("revoked",new JsonArray());
                for(String id:ids)report.getAsJsonArray("revoked").add(id);
                report.add("outcomes",outcomes);report.addProperty("diagnostic",EditorDiagnostics.path(root()));
                report.addProperty("diagnosticOutcome",diagnostic.has("outcome")?diagnostic.get("outcome").getAsString():"");
                Files.writeString(root().resolve("pack-revoke.json"),ProjectStore.JSON.toJson(report));
                phase=99;mc.stop();return;
            }
            phase++;ticks=0;
        }catch(Throwable error){throw fail(error);}
    }
}
