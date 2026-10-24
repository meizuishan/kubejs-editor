package dev.kubejseditor.script;

import java.util.*;

/** UTF-16 positions match Minecraft's text input; snapshots include the selection. */
public final class ScriptBuffer {
    public record Snapshot(String text,int cursor,int anchor){}
    private final Deque<Snapshot> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public String text;public int cursor,anchor;
    public ScriptBuffer(String text){this.text=text;}
    public boolean selected(){return cursor!=anchor;}
    public String selection(){return text.substring(Math.min(cursor,anchor),Math.max(cursor,anchor));}
    public void move(int position,boolean selecting){cursor=Math.max(0,Math.min(text.length(),position));if(!selecting)anchor=cursor;}
    public void replace(String value){
        undo.push(new Snapshot(text,cursor,anchor));if(undo.size()>200)undo.removeLast();redo.clear();
        int start=Math.min(cursor,anchor),end=Math.max(cursor,anchor);text=text.substring(0,start)+value+text.substring(end);cursor=anchor=start+value.length();
    }
    public void undo(){if(!undo.isEmpty()){redo.push(snapshot());restore(undo.pop());}}
    public void redo(){if(!redo.isEmpty()){undo.push(snapshot());restore(redo.pop());}}
    public int lineStart(){return text.lastIndexOf('\n',Math.max(-1,cursor-1))+1;}
    public int lineEnd(){int i=text.indexOf('\n',cursor);return i<0?text.length():i;}
    public void vertical(int delta,boolean selecting){int column=cursor-lineStart(),start=lineStart();if(delta<0){if(start==0){move(0,selecting);return;}int prev=text.lastIndexOf('\n',start-2)+1;move(Math.min(start-1,prev+column),selecting);}else{int end=lineEnd();if(end==text.length()){move(end,selecting);return;}int next=text.indexOf('\n',end+1);move(Math.min(next<0?text.length():next,end+1+column),selecting);}}
    public boolean find(String query){if(query.isEmpty())return false;int pos=text.indexOf(query,Math.max(cursor,anchor));if(pos<0)pos=text.indexOf(query);if(pos<0)return false;anchor=pos;cursor=pos+query.length();return true;}
    public void indent(boolean outdent){
        if(!selected()){if(outdent){int start=lineStart();int count=0;while(count<4&&start+count<text.length()&&text.charAt(start+count)==' ')count++;anchor=start;cursor=start+count;replace("");}else replace("    ");return;}
        int start=text.lastIndexOf('\n',Math.max(-1,Math.min(cursor,anchor)-1))+1,end=Math.max(cursor,anchor);String part=text.substring(start,end);anchor=start;cursor=end;
        replace(outdent?part.replaceAll("(?m)^ {1,4}",""):"    "+part.replace("\n","\n    "));anchor=start;
    }
    private Snapshot snapshot(){return new Snapshot(text,cursor,anchor);}
    private void restore(Snapshot s){text=s.text;cursor=s.cursor;anchor=s.anchor;}
}
