package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

public final class SequenceLayout {
    public static final int CARD_WIDTH=142,CARD_HEIGHT=150;
    public static final int VISIBLE_WIDTH=371; // Two full cards, two gaps, and half of the third card.
    public static int viewportWidth(int available){return Math.max(1,Math.min(VISIBLE_WIDTH,available));}
    public static double scale(int available){return viewportWidth(available)/(double)VISIBLE_WIDTH;}
    public static int columns(JsonObject d){return Math.max(1,d.getAsJsonArray("sequence").size());}
    public static int rows(JsonObject d){return 1;}
    public static int cardX(JsonObject d,int i){return i%columns(d)*(CARD_WIDTH+8);}
    public static int cardY(JsonObject d,int i){return i/columns(d)*(CARD_HEIGHT+8);}
    public static int summaryY(JsonObject d){return rows(d)*(CARD_HEIGHT+8)+24;}
    public static List<RecipeCanvas.Slot> slots(JsonObject d){
        List<RecipeCanvas.Slot> out=new ArrayList<>();int y=summaryY(d);
        property(out,d,"ingredient",8,y,"原料",false,false);property(out,d,"transitional_item",78,y,"中间件",true,false);
        JsonArray results=d.getAsJsonArray("results");for(int i=0;i<Math.max(1,results.size());i++){int index=i;out.add(new RecipeCanvas.Slot(156+i%6*30,y+i/6*40,"最终产物 "+(i+1),true,false,()->index<results.size()?results.get(index):JsonNull.INSTANCE,v->{if(v.isJsonNull()&&index>0)results.remove(index);else{while(results.size()<=index)results.add(JsonNull.INSTANCE);results.set(index,v);}}));}
        var steps=d.getAsJsonArray("sequence");for(int i=0;i<steps.size();i++){
            int sx=cardX(d,i),sy=cardY(d,i),step=i;var obj=steps.get(i).getAsJsonObject();var in=obj.getAsJsonArray("ingredients");String type=obj.get("type").getAsString();
            if(type.equals("create:deploying")||type.equals("create:filling"))out.add(new RecipeCanvas.Slot(sx+8,sy+40,"步骤 "+(step+1),false,type.equals("create:filling"),()->in.size()>1?in.get(1):JsonNull.INSTANCE,v->{while(in.size()<2)in.add(JsonNull.INSTANCE);in.set(1,v.isJsonNull()&&type.equals("create:filling")?RecipeAdapters.parse("{amount:250}"):v);}));
            // The intermediate is shared; these slots make the before/after roles explicit.
            property(out,d,"transitional_item",sx+8,sy+108,"步骤中间件输入 "+(i+1),true,false);
            property(out,d,"transitional_item",sx+112,sy+108,"步骤中间件输出 "+(i+1),true,false);
        }return out;
    }
    private static void property(List<RecipeCanvas.Slot> out,JsonObject d,String key,int x,int y,String label,boolean output,boolean fluid){out.add(new RecipeCanvas.Slot(x,y,label,output,fluid,()->d.has(key)?d.get(key):JsonNull.INSTANCE,v->{d.add(key,v);RecipeCanvas.normalizeTransition(d);}));}
}
