package dev.kubejseditor.client;

import dev.kubejseditor.model.EditorProject.ScriptDraft;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.script.ScriptBuffer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ScriptEditorScreen extends Screen {
    private final Screen parent;private final ScriptDraft draft;private final ScriptBuffer buffer;
    private boolean writable;private String status="Ctrl+S 暂存 · Ctrl+F 查找 · Ctrl+Z 撤销 · Tab 缩进";
    private int topLine,horizontal;private EditBox search;
    public ScriptEditorScreen(Screen parent,String path)throws java.io.IOException{
        super(Component.literal("脚本 · "+path));this.parent=parent;draft=EditorScripts.open(path);buffer=new ScriptBuffer(draft.text);writable=!EditorRuntime.locked()&&(!EditorRuntime.state().isManaged(path)||draft.takeover);
    }
    @Override protected void init(){
        search=new EditBox(font,48,30,Math.max(100,width-280),20,UiText.component("查找文本"));addRenderableWidget(search);
        addRenderableWidget(Button.builder(UiText.component("查找下一个"),b->{if(!buffer.find(search.getValue()))status="未找到";else follow();}).bounds(width-222,30,90,20).build());
        addRenderableWidget(Button.builder(UiText.component("校验"),b->{try{EditorRuntime.validateScripts(Map.of(draft.path,buffer.text.getBytes(StandardCharsets.UTF_8)));status="语法校验通过；事件与资源将在应用时验证";}catch(Exception e){status=e.getMessage();}}).bounds(width-128,30,80,20).build());
        addRenderableWidget(Button.builder(UiText.component(writable?"暂存草稿":"转为手写"),b->{
            if(!writable){writable=true;draft.takeover=true;status="已选择转为手写；应用后停止自动重写该文件";rebuildWidgets();}
            else save();
        }).bounds(16,height-30,100,20).build()).active=!EditorRuntime.locked();
        addRenderableWidget(Button.builder(UiText.component("返回"),b->onClose()).bounds(width-96,height-30,80,20).build());
    }
    private void save(){try{draft.text=buffer.text;EditorScripts.stage(draft);status="已加入操作记录，返回工作区后一键应用";}catch(Exception e){status="错误："+e.getMessage();}}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFF101923);g.drawString(font,font.plainSubstrByWidth(title.getString(),width-32),16,12,0xFF7DD3FC,false);
        String[] lines=buffer.text.split("\n",-1);int rows=Math.max(1,(height-116)/12),position=0;
        topLine=Math.min(topLine,Math.max(0,lines.length-rows));g.enableScissor(12,58,width-12,height-58);
        for(int i=0;i<lines.length;i++){
            int start=position,end=start+lines[i].length();position=end+1;if(i<topLine||i>=topLine+rows)continue;
            int y=62+(i-topLine)*12,x=48-horizontal;g.drawString(font,String.valueOf(i+1),14,y,0xFF64748B,false);
            int a=Math.max(start,Math.min(buffer.cursor,buffer.anchor)),b=Math.min(end,Math.max(buffer.cursor,buffer.anchor));
            if(b>a)g.fill(x+font.width(lines[i].substring(0,a-start)),y-1,x+font.width(lines[i].substring(0,b-start)),y+10,0xFF345570);
            g.drawString(font,lines[i],x,y,writable?0xFFE2E8F0:0xFF94A3B8,false);
            if(getFocused()!=search&&buffer.cursor>=start&&buffer.cursor<=end&&System.currentTimeMillis()/500%2==0){int cx=x+font.width(lines[i].substring(0,buffer.cursor-start));g.fill(cx,y-1,cx+1,y+10,0xFF7DD3FC);}
        }g.disableScissor();g.drawString(font,font.plainSubstrByWidth(status,width-32),16,height-48,0xFF94A3B8,false);
    }
    private int position(double x,double y){String[] lines=buffer.text.split("\n",-1);int line=Math.max(0,Math.min(lines.length-1,topLine+(int)(y-62)/12)),p=0;for(int i=0;i<line;i++)p+=lines[i].length()+1;return p+font.plainSubstrByWidth(lines[line],Math.max(0,(int)x-48+horizontal)).length();}
    @Override public boolean mouseClicked(double x,double y,int button){if(super.mouseClicked(x,y,button))return true;if(y>=58&&y<height-58&&button==0){setFocused(null);buffer.move(position(x,y),hasShiftDown());setDragging(true);return true;}return false;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(getFocused()==null&&button==0){buffer.move(position(x,y),true);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(hasShiftDown()||dx!=0)horizontal=Math.max(0,horizontal-(int)(dx!=0?dx:dy)*24);else topLine=Math.max(0,topLine-(int)dy*3);return true;}
    private void follow(){int line=(int)buffer.text.substring(0,buffer.cursor).chars().filter(c->c=='\n').count();int rows=Math.max(1,(height-116)/12);if(line<topLine)topLine=line;if(line>=topLine+rows)topLine=line-rows+1;String prefix=buffer.text.substring(buffer.lineStart(),buffer.cursor);int x=font.width(prefix);if(x<horizontal)horizontal=x;if(x>horizontal+width-80)horizontal=x-width+80;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        boolean control=hasControlDown(),shift=hasShiftDown();
        if(control&&key==GLFW.GLFW_KEY_F){setFocused(search);return true;}
        if(control&&key==GLFW.GLFW_KEY_S){if(writable)save();return true;}
        if(getFocused()==search)return super.keyPressed(key,scan,modifiers);
        if(control&&key==GLFW.GLFW_KEY_A){buffer.anchor=0;buffer.cursor=buffer.text.length();return true;}
        if(control&&key==GLFW.GLFW_KEY_C){minecraft.keyboardHandler.setClipboard(buffer.selection());return true;}
        if(control&&key==GLFW.GLFW_KEY_X&&writable){minecraft.keyboardHandler.setClipboard(buffer.selection());buffer.replace("");}
        else if(control&&key==GLFW.GLFW_KEY_V&&writable)buffer.replace(minecraft.keyboardHandler.getClipboard().replace("\r\n","\n"));
        else if(control&&key==GLFW.GLFW_KEY_Z&&writable){if(shift)buffer.redo();else buffer.undo();}
        else if(control&&key==GLFW.GLFW_KEY_Y&&writable)buffer.redo();
        else if(key==GLFW.GLFW_KEY_LEFT)buffer.move(buffer.cursor-1,shift);
        else if(key==GLFW.GLFW_KEY_RIGHT)buffer.move(buffer.cursor+1,shift);
        else if(key==GLFW.GLFW_KEY_UP)buffer.vertical(-1,shift);
        else if(key==GLFW.GLFW_KEY_DOWN)buffer.vertical(1,shift);
        else if(key==GLFW.GLFW_KEY_HOME)buffer.move(control?0:buffer.lineStart(),shift);
        else if(key==GLFW.GLFW_KEY_END)buffer.move(control?buffer.text.length():buffer.lineEnd(),shift);
        else if(key==GLFW.GLFW_KEY_PAGE_UP){topLine=Math.max(0,topLine-20);buffer.vertical(-1,shift);}
        else if(key==GLFW.GLFW_KEY_PAGE_DOWN){topLine+=20;buffer.vertical(1,shift);}
        else if(writable&&key==GLFW.GLFW_KEY_TAB)buffer.indent(shift);
        else if(writable&&key==GLFW.GLFW_KEY_ENTER)buffer.replace("\n");
        else if(writable&&key==GLFW.GLFW_KEY_BACKSPACE){if(!buffer.selected())buffer.anchor=Math.max(0,buffer.cursor-1);buffer.replace("");}
        else if(writable&&key==GLFW.GLFW_KEY_DELETE){if(!buffer.selected())buffer.anchor=Math.min(buffer.text.length(),buffer.cursor+1);buffer.replace("");}
        else return super.keyPressed(key,scan,modifiers);
        follow();return true;
    }
    @Override public boolean charTyped(char c,int modifiers){if(getFocused()==search)return super.charTyped(c,modifiers);if(writable&&!Character.isISOControl(c)){buffer.replace(String.valueOf(c));follow();return true;}return false;}
    @Override public void onClose(){if(writable&&(draft.takeover||!buffer.text.equals(draft.text))){save();if(status.startsWith("错误"))return;}minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
