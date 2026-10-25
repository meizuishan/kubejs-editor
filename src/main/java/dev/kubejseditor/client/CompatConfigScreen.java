package dev.kubejseditor.client;

import dev.kubejseditor.config.CreateConfigs;
import dev.kubejseditor.runtime.EditorRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Global machine settings use the existing validated config transaction, never recipe JSON. */
public final class CompatConfigScreen {
    public static void open(Screen parent,String mod){var choices=new ArrayList<ChoiceScreen.Choice>();for(var entry:CreateConfigs.forMod(mod))choices.add(new ChoiceScreen.Choice(ConfigLabels.choice(entry),()->{if(!entry.editable()){Minecraft.getInstance().setScreen(new TextScreen(parent,"配置类型暂不可编辑",ConfigLabels.description(entry)));return;}Minecraft.getInstance().setScreen(new CompatValueScreen(parent,"全局机器配置 · "+ConfigLabels.name(entry)+"（需重启）",entry.text(entry.current()),text->{EditorRuntime.requireEditable();CreateConfigs.queue(entry,text);try{EditorRuntime.saveDraft();}catch(Exception e){throw new IllegalStateException(e);}}));},ConfigLabels.description(entry)));Minecraft.getInstance().setScreen(choices.isEmpty()?new TextScreen(parent,"模组配置","此模组没有已加载且可公开读取的 ModConfigSpec；展示数值不代表可写入的配方字段。"):new ChoiceScreen(parent,"全局机器配置（加入待办后回工作区应用；需重启）",choices));}
    private CompatConfigScreen(){}
}
