package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Device roles, rather than a generic ingredient strip. Coordinates follow Create's JEI scenes. */
public final class CreateProcessingLayout {
    public static List<RecipeCanvas.Slot> slots(JsonObject d){
        List<RecipeCanvas.Slot> out=new ArrayList<>();String type=d.get("type").getAsString();JsonArray in=d.getAsJsonArray("ingredients"),results=d.getAsJsonArray("results");
        boolean basin=type.equals("create:mixing")||type.equals("create:compacting")||VintageProcessing.basin(type);
        if(basin){int items=0,fluids=0;for(JsonElement e:in)if(RecipeLimits.fluid(e))fluids++;else items++;int ni=0,nf=0;
            for(int i=0;i<Math.max(1,in.size());i++){boolean fluid=i<in.size()&&RecipeLimits.fluid(in.get(i));int n=fluid?nf++:ni++;slot(out,in,i,8+(n%2)*28,fluid?28+Math.max(1,(items+1)/2)*36+n/2*36:12+n/2*36,fluid?"输入流体 "+(n+1):"盆内原料 "+(n+1),false,fluid,1);}
        }else{
            boolean second=Set.of("create:deploying","create:item_application","create:filling").contains(type);
            slot(out,in,0,8,92,type.equals("create:deploying")||type.equals("create:filling")?"置物台原料":"待加工物品",false,false,second?2:1);
            if(second)slot(out,in,1,32,8,type.equals("create:filling")?"注液流体":type.equals("create:deploying")?"机械手持物":"手持材料",false,type.equals("create:filling"),2);
            for(int i=second?2:1;i<in.size();i++)slot(out,in,i,8+(i%3)*28,116+i/3*36,"额外原料 "+i,false,RecipeLimits.fluid(in.get(i)),second?2:1);
        }
        for(int i=0;i<Math.max(1,results.size());i++){boolean fluid=i<results.size()&&RecipeLimits.fluid(results.get(i));slot(out,results,i,(basin?170:180)+(i%3)*28,(basin?28:92)+(i/3)*38,(fluid?"流体产物 ":"产物 ")+(i+1),true,fluid,1);}
        return out;
    }
    private static void slot(List<RecipeCanvas.Slot> out,JsonArray a,int i,int x,int y,String label,boolean output,boolean fluid,int required){
        out.add(new RecipeCanvas.Slot(x,y,label,output,fluid,()->i<a.size()?a.get(i):JsonNull.INSTANCE,v->{
            if(v.isJsonNull()&&i>=required&&a.size()>required){a.remove(i);return;}
            while(a.size()<=i)a.add(JsonNull.INSTANCE);a.set(i,v.isJsonNull()&&fluid?RecipeAdapters.parse("{amount:250}"):v.deepCopy());
        }));
    }
}
