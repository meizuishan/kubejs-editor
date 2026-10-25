package dev.kubejseditor.client;

import dev.kubejseditor.model.RegistryEntryTarget;
import dev.kubejseditor.runtime.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import java.util.*;

/** Manual fallback lists actual server data. Selecting an entry is explicit; no output-based guessing. */
public final class GenericDataBrowser {
    public static void open(Screen parent,IRecipeLayoutDrawable<?> layout){var mc=Minecraft.getInstance();if(!mc.hasSingleplayerServer()){mc.setScreen(new TextScreen(parent,"实际数据来源","此入口需要本机服务端。当前可以查看 JEI 展示。"));return;}var choices=List.of(new ChoiceScreen.Choice("普通配方（重载生效）",()->recipes(parent,layout)),new ChoiceScreen.Choice("数据包注册表（重启生效）",()->registries(parent,layout)),new ChoiceScreen.Choice("模组全局机器配置",()->configs(parent)));mc.setScreen(new ChoiceScreen(parent,"选择真实数据 · 不会仅凭展示推断来源",choices));}
    private static void recipes(Screen parent,IRecipeLayoutDrawable<?> layout){var choices=Minecraft.getInstance().getSingleplayerServer().getRecipeManager().getRecipes().stream().map(h->h.id().toString()).sorted().map(id->new ChoiceScreen.Choice(id,()->confirm(parent,layout,id))).toList();Minecraft.getInstance().setScreen(new ChoiceScreen(parent,"选择普通配方 ID · 仅在选中后读取完整数据",choices));}
    private static void registries(Screen parent,IRecipeLayoutDrawable<?> layout){var choices=net.neoforged.neoforge.registries.DataPackRegistriesHooks.getDataPackRegistriesWithDimensions().sorted(Comparator.comparing(d->d.key().location().toString())).map(d->new ChoiceScreen.Choice(d.key().location().toString(),()->entries(parent,layout,d.key()))).toList();Minecraft.getInstance().setScreen(new ChoiceScreen(parent,"选择已声明真实数据编码器的注册表",choices));}
    private static <T> void entries(Screen parent,IRecipeLayoutDrawable<?> layout,net.minecraft.resources.ResourceKey<? extends Registry<T>> key){var registry=Minecraft.getInstance().getSingleplayerServer().registryAccess().registry(key);if(registry.isEmpty()){Minecraft.getInstance().setScreen(new TextScreen(parent,"注册表未加载",key.location().toString()));return;}var choices=registry.get().keySet().stream().map(ResourceLocation::toString).sorted().map(id->new ChoiceScreen.Choice(id,()->confirm(parent,layout,new RegistryEntryTarget(key.location().toString(),id).id()))).toList();Minecraft.getInstance().setScreen(new ChoiceScreen(parent,"选择实际条目 · 修改影响所有使用者",choices));}
    private static void confirm(Screen parent,IRecipeLayoutDrawable<?> layout,String id){var actual=EditorRuntime.recipe(id);if(actual==null||actual.data()==null){Minecraft.getInstance().setScreen(new TextScreen(parent,"来源无法编辑",actual==null?"来源已卸载":actual.error()));return;}Minecraft.getInstance().setScreen(new ConfirmScreen(yes->{if(yes){JeiRecipeSources.associate(layout,id);JeiRecipeSources.capture(layout,ResourceLocation.parse(id));CompatibilityScreenOpen.open(parent,id);}else Minecraft.getInstance().setScreen(parent);},Component.literal("确认实际修改目标"),Component.literal("选择来源："+(actual.registryTarget()==null?id:actual.registryTarget().entry)+"。手动选择没有自动证明它与当前 JEI 展示的关联；修改作用于这条实际数据及其所有使用者。"),Component.literal("编辑此数据源"),Component.literal("取消")));}
    private static void configs(Screen parent){var mods=dev.kubejseditor.config.CreateConfigs.all().stream().map(e->e.config().getModId()).distinct().sorted().map(id->new ChoiceScreen.Choice(id,()->CompatConfigScreen.open(parent,id))).toList();Minecraft.getInstance().setScreen(new ChoiceScreen(parent,"选择实际已加载配置的模组 · 全局设置",mods));}
    private GenericDataBrowser(){}
}
