package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

/** Full-size multiline input used for exact JSON values and mapping documents. */
public final class CompatValueScreen extends Screen {
    private final Screen parent;private final String initial;private final Consumer<String> save;private MultiLineEditBox input;private String status="Ctrl+A 全选 · Ctrl+V 粘贴 · 保存前检查格式";
    public CompatValueScreen(Screen parent,String title,String initial,Consumer<String> save){super(Component.literal(title));this.parent=parent;this.initial=initial;this.save=save;}
    @Override protected void init(){input=new MultiLineEditBox(font,16,36,width-32,height-98,Component.literal("输入 JSON 或字段值"),Component.literal("字段值"));input.setValue(initial);input.setCharacterLimit(262144);addRenderableWidget(input);setInitialFocus(input);addRenderableWidget(Button.builder(Component.literal("检查并保存"),b->{try{save.accept(input.getValue());minecraft.setScreen(parent);}catch(Exception e){status="错误："+e.getMessage();}}).bounds(16,height-30,110,20).build());addRenderableWidget(Button.builder(Component.literal("取消"),b->onClose()).bounds(width-96,height-30,80,20).build());}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF101923);g.drawString(font,title,16,14,0xFF7DD3FC,false);g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-52,0xFFFFCC88,false);}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
