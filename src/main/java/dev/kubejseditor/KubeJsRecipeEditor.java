package dev.kubejseditor;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(KubeJsRecipeEditor.MOD_ID)
public class KubeJsRecipeEditor {
    public static final String MOD_ID = "kubejs_editor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KubeJsRecipeEditor(IEventBus modEventBus, ModContainer modContainer) {
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        modEventBus.addListener(dev.kubejseditor.runtime.GeneratedDataPack::register);
    }
}
