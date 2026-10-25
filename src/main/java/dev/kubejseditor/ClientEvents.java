package dev.kubejseditor;
import net.minecraft.commands.Commands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import dev.kubejseditor.client.WorkspaceScreen;
@EventBusSubscriber(modid = KubeJsRecipeEditor.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {
    @SubscribeEvent public static void recipes(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event){dev.kubejseditor.runtime.EditorRuntime.recipesChanged();}
    @SubscribeEvent public static void logout(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){dev.kubejseditor.runtime.EditorRuntime.recipesChanged();dev.kubejseditor.client.CompatJeiLayouts.reset();dev.kubejseditor.runtime.RegistryDataSources.clear();}
    public static void open() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null)return;
        try { mc.setScreen(new WorkspaceScreen()); }
        catch(Exception e) { mc.player.sendSystemMessage(Component.literal("KubeJS Editor: "+e.getMessage())); }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("kjseditor").executes(ctx->{Minecraft.getInstance().tell(ClientEvents::open);return 1;}));
        event.getDispatcher().register(Commands.literal("kubejseditor").executes(ctx->{Minecraft.getInstance().tell(ClientEvents::open);return 1;}));
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        dev.kubejseditor.runtime.EditorRuntime.pollActivation();
        if(ClientSetup.OPEN_EXPORTER_KEY.consumeClick()&&Minecraft.getInstance().screen==null)open();
    }
}
