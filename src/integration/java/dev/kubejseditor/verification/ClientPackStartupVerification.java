package dev.kubejseditor.verification;

import com.google.gson.JsonObject;
import dev.kubejseditor.storage.ProjectStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;

/** Loads an isolated pack to its title screen; never opens player worlds. */
@EventBusSubscriber(modid="kubejs_editor_verification",value=Dist.CLIENT)
public final class ClientPackStartupVerification {
    private static boolean done;private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("kubejs.editor.verifyPackStartup")||done)return;var mc=Minecraft.getInstance();
        if(!(mc.screen instanceof TitleScreen)||++ticks<60)return;
        try{var report=new JsonObject();report.addProperty("passed",true);report.addProperty("titleScreen",true);report.addProperty("loadedMods",ModList.get().size());
            for(String id:new String[]{"neoforge","kubejs","jei","create","kubejs_editor"})report.addProperty(id,ModList.get().getModContainerById(id).orElseThrow().getModInfo().getVersion().toString());
            Files.writeString(mc.gameDirectory.toPath().resolve("pack-startup.json"),ProjectStore.JSON.toJson(report));
        }catch(Exception e){throw new RuntimeException(e);}done=true;mc.stop();
    }
}
