package dev.kubejseditor;

import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import dev.kubejseditor.gui.RecipeBuilderMenu;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, KubeJsRecipeEditor.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RecipeBuilderMenu>> RECIPE_BUILDER =
            MENU_TYPES.register("recipe_builder",
                    () -> IMenuTypeExtension.create(RecipeBuilderMenu.FACTORY));
    public static final DeferredHolder<MenuType<?>,MenuType<dev.kubejseditor.content.StorageMenu>> STORAGE=MENU_TYPES.register("storage",()->IMenuTypeExtension.create(dev.kubejseditor.content.StorageMenu::new));
}
