package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject.*;
import java.util.*;

public final class CraftingGrid {
    private static final String SYMBOLS="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$%&()*+,-./:;<=>?@[]^_{|}~";
    public static boolean supports(JsonObject data){if(data==null||!data.has("type"))return false;String t=data.get("type").getAsString();return t.equals("minecraft:crafting_shaped")||t.equals("minecraft:crafting_shapeless")||t.equals("create:mechanical_crafting");}
    public static void ensure(RecipeDraft draft){
        if(!supports(draft.data)||draft.grid!=null)return;
        var data=draft.data;GridDraft g=new GridDraft();
        if(data.get("type").getAsString().equals("create:mechanical_crafting")&&data.has("pattern")){var p=data.getAsJsonArray("pattern");g.size=Math.max(3,Math.max(p.size(),p.isEmpty()?0:p.get(0).getAsString().length()));}
        for(int i=0;i<g.size*g.size;i++)g.cells.add(JsonNull.INSTANCE);
        if(data.has("pattern")){
            var p=data.getAsJsonArray("pattern");var key=data.getAsJsonObject("key");
            for(int y=0;y<p.size();y++)for(int x=0;x<p.get(y).getAsString().length();x++){String c=String.valueOf(p.get(y).getAsString().charAt(x));if(!c.equals(" ")&&key.has(c))g.cells.set(y*g.size+x,key.get(c).deepCopy());}
        }else if(data.has("ingredients")){int i=0;for(var e:data.getAsJsonArray("ingredients")){if(i>=9)throw new IllegalArgumentException("工作台原料超过 9 格，无法无损导入");g.cells.set(i++,e.deepCopy());}}
        draft.grid=g;
    }
    public static void resize(RecipeDraft draft,int size){
        ensure(draft);if(!draft.data.get("type").getAsString().equals("create:mechanical_crafting"))throw new IllegalArgumentException("只有动力合成可调整网格");
        if(size<1||size>9)throw new IllegalArgumentException("动力合成尺寸须为 1～9");GridDraft old=draft.grid;
        for(int y=0;y<old.size;y++)for(int x=0;x<old.size;x++)if((x>=size||y>=size)&&!old.cells.get(y*old.size+x).isJsonNull())throw new IllegalArgumentException("缩小会丢失材料，请先清空超出新尺寸的格子");
        GridDraft g=new GridDraft();g.size=size;for(int y=0;y<size;y++)for(int x=0;x<size;x++)g.cells.add(x<old.size&&y<old.size?old.cells.get(y*old.size+x).deepCopy():JsonNull.INSTANCE);draft.grid=g;sync(draft);
    }
    public static void setOrdered(RecipeDraft draft,boolean ordered){ensure(draft);if(draft.data.get("type").getAsString().equals("create:mechanical_crafting"))throw new IllegalArgumentException("动力合成不支持无序切换");draft.data.addProperty("type",ordered?"minecraft:crafting_shaped":"minecraft:crafting_shapeless");sync(draft);}
    public static void sync(RecipeDraft draft){if(draft.grid!=null)write(draft.grid,draft.data);}
    public static JsonObject generated(RecipeDraft draft){JsonObject data=draft.data.deepCopy();if(draft.grid!=null)write(draft.grid,data);return data;}
    private static void write(GridDraft g,JsonObject data){
        if(data.get("type").getAsString().equals("minecraft:crafting_shapeless")){
            JsonArray ingredients=new JsonArray();for(var e:g.cells)if(!e.isJsonNull())ingredients.add(e.deepCopy());data.remove("pattern");data.remove("key");data.remove("show_notification");data.add("ingredients",ingredients);return;
        }
        int minX=g.size,minY=g.size,maxX=-1,maxY=-1;
        for(int y=0;y<g.size;y++)for(int x=0;x<g.size;x++)if(!g.cells.get(y*g.size+x).isJsonNull()){minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);}
        JsonArray pattern=new JsonArray();JsonObject key=new JsonObject();List<JsonElement> distinct=new ArrayList<>();
        if(maxX<0)pattern.add(" ");else for(int y=minY;y<=maxY;y++){
            StringBuilder row=new StringBuilder();for(int x=minX;x<=maxX;x++){JsonElement e=g.cells.get(y*g.size+x);if(e.isJsonNull()){row.append(' ');continue;}int n=distinct.indexOf(e);if(n<0){n=distinct.size();distinct.add(e);key.add(String.valueOf(SYMBOLS.charAt(n)),e.deepCopy());}row.append(SYMBOLS.charAt(n));}pattern.add(row.toString());
        }
        data.remove("ingredients");data.add("pattern",pattern);data.add("key",key);
    }
    public static List<RecipeCanvas.Slot> slots(RecipeDraft draft){
        ensure(draft);List<RecipeCanvas.Slot> out=new ArrayList<>();GridDraft grid=draft.grid;
        for(int y=0;y<grid.size;y++)for(int x=0;x<grid.size;x++){int index=y*grid.size+x;out.add(new RecipeCanvas.Slot(x*24,y*24,"材料 "+(y+1)+","+(x+1),false,false,()->grid.cells.get(index),v->{grid.cells.set(index,v.deepCopy());sync(draft);}));}
        out.add(new RecipeCanvas.Slot(grid.size*24+48,grid.size/2*24,"输出",true,false,()->draft.data.has("result")?draft.data.get("result"):JsonNull.INSTANCE,v->draft.data.add("result",v.deepCopy())));return out;
    }
}
