package dev.kubejseditor.recipe;

import com.google.gson.*;
import java.util.*;

/** Resolves codec-style field pointers without altering recipe data. */
public final class RecipeIssueTarget {
    public static RecipeCanvas.Slot find(JsonObject data,List<RecipeCanvas.Slot> slots,String field){
        String[] parts=field.split("/");if(parts.length<2)return null;
        try{
            if(parts[1].equals("sequence")&&parts.length>2){String label="步骤 "+(Integer.parseInt(parts[2])+1);return slots.stream().filter(s->s.label().equals(label)).findFirst().orElse(null);}
            if(Set.of("ingredients","results").contains(parts[1])){
                boolean output=parts[1].equals("results");var candidates=slots.stream().filter(s->output?s.output():!s.output()).filter(s->!data.has("sequence")||s.label().startsWith("最终产物")).toList();
                int index=parts.length>2?Integer.parseInt(parts[2]):Math.min(candidates.size()-1,RecipeLimits.forType(data.get("type").getAsString()).max(output,false));
                return candidates.isEmpty()?null:candidates.get(Math.max(0,Math.min(index,candidates.size()-1)));
            }
            if(data.has(parts[1]))return slots.stream().filter(s->s.get()==data.get(parts[1])).findFirst().orElse(null);
        }catch(Exception ignored){}
        return null;
    }
    private RecipeIssueTarget(){}
}
