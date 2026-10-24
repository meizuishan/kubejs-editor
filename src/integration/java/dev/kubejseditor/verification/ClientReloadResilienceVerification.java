package dev.kubejseditor.verification;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.recipe.RecipeAdapters;
import dev.kubejseditor.runtime.EditorDiagnostics;
import dev.kubejseditor.runtime.EditorRuntime;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Reproduces the Skyhive failure mode in isolation: a third-party OnDatapackSyncEvent
 * listener throws before PlayerList sends recipe/tag packets. The editor must verify the
 * server result, compensate the client packets, and only then promote the transaction.
 */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientReloadResilienceVerification {
    private static final String ADDED="kubejs_editor:resilience_added";
    private static final String REPLACED="minecraft:oak_button";
    private static final String RETRY="kubejs_editor:resilience_retry";
    private static final String ERROR="kubejs/server_scripts/resilience_expected_error.js";
    private static int phase,ticks,checks,syncFailures;
    private static EditorRuntime.ReloadResult result;
    private static boolean failNextSync;
    private static JsonObject baseline;
    private static Path root(){return Minecraft.getInstance().gameDirectory.toPath();}
    private static void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    private static boolean serverExists(String id){var server=Minecraft.getInstance().getSingleplayerServer();return server!=null&&server.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    private static boolean clientExists(String id){var level=Minecraft.getInstance().level;return level!=null&&level.getRecipeManager().byKey(ResourceLocation.parse(id)).isPresent();}
    @SuppressWarnings("unchecked") private static boolean crafts(String id,Item item){var mc=Minecraft.getInstance();var found=mc.getSingleplayerServer().getRecipeManager().byKey(ResourceLocation.parse(id));return found.isPresent()&&((Recipe<CraftingInput>)found.get().value()).matches(CraftingInput.of(1,1,List.of(new ItemStack(item))),mc.level);}
    private static void ready(){check(result!=null&&result.success(),"重载成功："+result);check(EditorRuntime.activationError.isBlank(),"成功后 activationError 为空");}
    private static void apply(EditorProject.RecipeDraft draft)throws Exception{
        EditorRuntime.project().recipes.put(draft.id,draft);EditorRuntime.saveDraft();
        var review=EditorRuntime.review();check(!review.restartRequired(),"配方事务可热重载");EditorRuntime.apply(review);
        result=null;EditorRuntime.reloadResult(false,r->result=r);
    }
    private static void revokeWithSyncFailure(String id)throws Exception{result=null;failNextSync=true;EditorRuntime.revokeRecipe(id,r->result=r);}
    private static void revokeNormally(String id)throws Exception{result=null;failNextSync=false;EditorRuntime.revokeRecipe(id,r->result=r);}
    private static void reloadNormally()throws Exception{result=null;failNextSync=false;EditorRuntime.reloadResult(false,r->result=r);}
    private static JsonObject readDiagnostic()throws Exception{return JsonParser.parseString(EditorDiagnostics.read(root())).getAsJsonObject();}
    private static EditorProject.RecipeDraft shapeless(String id,String item){var draft=new EditorProject.RecipeDraft(id,RecipeAdapters.parse("{type:'minecraft:crafting_shapeless',ingredients:[{item:'"+item+"'}],result:{id:'minecraft:oak_button'}}"));draft.operation="add";draft.origin="add";return draft;}
    @SubscribeEvent public static void sync(OnDatapackSyncEvent event){
        if(!failNextSync)return;failNextSync=false;syncFailures++;
        throw new NullPointerException("Data resource not present: null [reload-resilience verification]");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyReloadResilience")||phase==99)return;
        var mc=Minecraft.getInstance();ticks++;
        if(ticks>3600)throw fail(new AssertionError("超时，阶段 "+phase+" · "+EditorRuntime.activationError));
        try{
            if(phase==0&&mc.screen instanceof TitleScreen&&ticks>80){mc.createWorldOpenFlows().openWorld("EditorVerification",()->{});phase=1;ticks=0;return;}
            if(phase==1&&mc.level!=null&&mc.player!=null&&ticks>100){
                check(EditorRuntime.state().applied.recipes.isEmpty()&&EditorRuntime.project().recipes.isEmpty(),"隔离世界以空工作区启动");
                baseline=EditorRuntime.recipes().stream().filter(r->r.id().equals(REPLACED)).findFirst().orElseThrow().data();
                check(crafts(REPLACED,Items.OAK_PLANKS),"原始橡木按钮使用橡木木板");
                apply(shapeless(ADDED,"minecraft:iron_ingot"));phase=2;ticks=0;return;
            }
            if(phase>=2&&result!=null&&EditorRuntime.busy())return;
            switch(phase){
                case 2->{if(result==null)return;ready();check(serverExists(ADDED),"新增配方已加载");
                    var replace=new EditorProject.RecipeDraft(REPLACED,baseline.deepCopy());replace.operation="replace";replace.origin="existing";replace.source=baseline.deepCopy();
                    replace.data.add("ingredients",JsonParser.parseString("[{item:'minecraft:diamond'}]"));apply(replace);}
                case 3->{if(result==null)return;ready();check(crafts(REPLACED,Items.DIAMOND)&&!crafts(REPLACED,Items.OAK_PLANKS),"替换配方确实使用钻石");revokeWithSyncFailure(ADDED);}
                case 4->{if(result==null)return;ready();check(syncFailures==1,"第一次撤销触发了模拟的同步监听异常");
                    check(!EditorRuntime.state().applied.recipes.containsKey(ADDED),"撤销新增后记录消失");check(!serverExists(ADDED),"撤销新增后服务端配方消失");
                    check(result.message().contains("补偿"),"结果说明客户端已补偿同步："+result.message());
                    var diagnostic=readDiagnostic();check("compensated-success".equals(diagnostic.get("outcome").getAsString()),"诊断记录补偿成功");
                    check(diagnostic.getAsJsonObject("error").get("type").getAsString().contains("NullPointerException"),"诊断保留第三方异常");
                    phase=5;ticks=0;return;}
                case 5->{if(ticks<20)return;check(!clientExists(ADDED),"补偿包到达客户端后配方消失");revokeWithSyncFailure(REPLACED);}
                case 6->{if(result==null)return;ready();check(syncFailures==2,"第二次撤销触发同步监听异常");
                    check(!EditorRuntime.state().applied.recipes.containsKey(REPLACED),"撤销替换后记录消失");check(serverExists(REPLACED),"撤销替换后来源配方恢复");
                    check(crafts(REPLACED,Items.OAK_PLANKS)&&!crafts(REPLACED,Items.DIAMOND),"撤销替换恢复原始橡木按钮");
                    check(!Files.exists(root().resolve(EditorRuntime.state().outputPath(dev.kubejseditor.storage.WorkspaceState.GENERATED.getFirst()))),"撤销最后一条规则时删除生成脚本");
                    phase=7;ticks=0;return;}
                case 7->{if(ticks<20)return;check(clientExists(REPLACED),"补偿包到达客户端后来源配方恢复");check(crafts(REPLACED,Items.OAK_PLANKS),"客户端可见恢复后的原始配方");
                    var retry=shapeless(RETRY,"minecraft:iron_ingot");apply(retry);}
                case 8->{if(result==null)return;ready();check(serverExists(RETRY),"失败路径准备：新增配方已加载");
                    Files.createDirectories(root().resolve(ERROR).getParent());
                    Files.writeString(root().resolve(ERROR),"ServerEvents.recipes(e => { throw new Error('RESILIENCE_EXPECTED_ERROR') })\n");revokeNormally(RETRY);}
                case 9->{if(result!=null&&EditorRuntime.busy())return;if(result==null)return;
                    check(!result.success(),"KJS 脚本错误使撤销失败");check(EditorRuntime.state().applied.recipes.containsKey(RETRY),"失败时保留已应用记录");
                    check(EditorRuntime.state().activation!=null&&EditorRuntime.state().activation.revokedRecipes.contains(RETRY),"失败时保留撤销事务");
                    var diagnostic=readDiagnostic();check("failure".equals(diagnostic.get("outcome").getAsString()),"诊断记录失败阶段");
                    check(!Files.readString(root().resolve(ERROR)).isBlank(),"错误脚本仍在，等待回滚");
                    Files.delete(root().resolve(ERROR));EditorRuntime.rollback();reloadNormally();}
                case 10->{if(result==null)return;ready();check(serverExists(RETRY),"回滚后新增配方恢复");revokeNormally(RETRY);}
                case 11->{if(result==null)return;ready();check(!serverExists(RETRY)&&!EditorRuntime.state().applied.recipes.containsKey(RETRY),"回滚后重试撤销成功");
                    check(EditorRuntime.project().recipes.isEmpty(),"撤销不产生额外待办");check(EditorRuntime.state().applied.recipes.isEmpty(),"工作区回到空状态");
                    var report=new JsonObject();report.addProperty("passed",true);report.addProperty("checks",checks);report.addProperty("syncFailures",syncFailures);
                    report.addProperty("diagnostic",EditorDiagnostics.path(root()));
                    Files.writeString(root().resolve("reload-resilience.json"),ProjectStore.JSON.toJson(report));phase=99;mc.stop();return;}
                default->{if(result==null)return;ready();}
            }
            phase++;ticks=0;
        }catch(Throwable error){throw fail(error);}
    }
    private static RuntimeException fail(Throwable error){
        try{var report=new JsonObject();report.addProperty("passed",false);report.addProperty("phase",phase);report.addProperty("checks",checks);report.addProperty("syncFailures",syncFailures);report.addProperty("error",String.valueOf(error));Files.writeString(root().resolve("reload-resilience.json"),ProjectStore.JSON.toJson(report));}catch(Exception ignored){}
        Minecraft.getInstance().stop();phase=99;
        if(error instanceof RuntimeException runtime)return runtime;
        return new RuntimeException(error);
    }
}
