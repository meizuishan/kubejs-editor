package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class TextScreen extends Screen {
    private final Screen parent;private final String content;private List<String> lines;private int offset;
    public TextScreen(Screen parent,String title,String content){super(UiText.component(title));this.parent=parent;this.content=content;}
    @Override protected void init(){
        lines=new ArrayList<>();for(String line:content.split("\n",-1)){
            if(line.isEmpty())lines.add("");else for(var wrapped:font.split(Component.literal(line),width-40)){
                StringBuilder s=new StringBuilder();wrapped.accept((i,style,c)->{s.appendCodePoint(c);return true;});lines.add(s.toString());
            }
        }
        addRenderableWidget(Button.builder(UiText.component("复制全文"),b->minecraft.keyboardHandler.setClipboard(content)).bounds(width/2-125,height-28,120,20).build());
        addRenderableWidget(Button.builder(UiText.component("返回"),b->onClose()).bounds(width/2+5,height-28,120,20).build());
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xFF111827);g.drawString(font,title,16,12,0xFF7DD3FC);
        int rows=Math.max(1,(height-75)/12);offset=Math.min(offset,Math.max(0,lines.size()-rows));
        for(int i=offset;i<Math.min(lines.size(),offset+rows);i++){String line=lines.get(i);g.drawString(font,line,16,34+(i-offset)*12,line.startsWith("+")?0xFF86EFAC:line.startsWith("-")?0xFFFCA5A5:0xFFCBD5E1,false);}super.render(g,x,y,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*4);return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
