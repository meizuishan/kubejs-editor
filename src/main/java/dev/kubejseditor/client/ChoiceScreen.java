package dev.kubejseditor.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

public final class ChoiceScreen extends Screen {
    public record Choice(String text,Runnable action,String searchText,String secondaryText,Runnable secondaryAction,BooleanSupplier secondaryEnabled) {
        public Choice(String text,Runnable action){this(text,action,"");}
        public Choice(String text,Runnable action,String searchText){this(text,action,searchText,"",null,()->false);}
    }
    private final Screen parent;private final List<Choice> choices;private String query="";private int offset;
    public ChoiceScreen(Screen parent,String title,List<Choice> choices){super(UiText.component(title));this.parent=parent;this.choices=choices;}
    @Override protected void init(){
        EditBox search=new EditBox(font,width/2-180,34,360,20,UiText.component("搜索"));search.setValue(query);
        search.setResponder(s->{query=s;offset=0;rebuildWidgets();});addRenderableWidget(search);
        setFocused(search);search.setCursorPosition(query.length());
        List<Choice> filtered=choices.stream().filter(c->(c.text+" "+UiText.text(c.text)+" "+c.searchText).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(height-108)/24);offset=Math.min(offset,Math.max(0,filtered.size()-rows));
        for(int i=offset;i<Math.min(filtered.size(),offset+rows);i++){
            Choice c=filtered.get(i);int y=64+(i-offset)*24,w=c.secondaryAction==null?360:282;
            var button=Button.builder(Component.literal(font.plainSubstrByWidth(UiText.text(c.text),w-12)),b->{c.action.run();if(minecraft.screen==this)minecraft.setScreen(parent);}).bounds(width/2-180,y,w,20).build();
            button.setTooltip(Tooltip.create(Component.literal(c.text+(c.searchText.isEmpty()?"":"\n"+c.searchText))));addRenderableWidget(button);
            if(c.secondaryAction!=null){
                var secondary=Button.builder(UiText.component(c.secondaryText),b->{c.secondaryAction.run();rebuildWidgets();}).bounds(width/2+106,y,74,20).build();
                secondary.active=c.secondaryEnabled.getAsBoolean();secondary.setTooltip(Tooltip.create(Component.literal((c.secondaryText.equals("加工机制")?"查看实际加工入口；展示配方不会改变动态产物":secondary.active?"禁用此配方，加入操作记录；一键应用后生效":"此配方已加入禁用待办，或当前批次已锁定")+"\n"+c.text)));addRenderableWidget(secondary);
            }
        }
        addRenderableWidget(Button.builder(UiText.component("返回"),b->onClose()).bounds(width/2-60,height-30,120,20).build());
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xEE111827);g.drawCenteredString(font,title,width/2,14,0xFFE2E8F0);super.render(g,x,y,partial);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){offset=Math.max(0,offset-(int)dy*3);rebuildWidgets();return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
