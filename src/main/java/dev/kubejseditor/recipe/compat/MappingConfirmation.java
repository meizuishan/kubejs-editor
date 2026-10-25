package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** An unresolved slot must not prevent confirming an independent, valid scalar field. */
public final class MappingConfirmation {
    public static CompatDefinition prepare(CompatDefinition original,JsonObject data){
        var gson=new Gson();var next=gson.fromJson(gson.toJson(original),CompatDefinition.class);next.check();
        var errors=new ArrayList<String>();
        for(var field:next.fields){
            if(!field.readOnlyReason.isBlank())continue;
            // Field checks are independent of whole-recipe rules (e.g. pedestal totals).
            var probe=new CompatDefinition("kubejs_editor:field_validation","confirmed");probe.fields.add(field);
            var issues=new RecipePresentation(data,probe).validate();
            if(issues.isEmpty())continue;
            String reason=field.path+"："+String.join("；",issues);
            if(field.slot())field.readOnlyReason="此槽位尚未完整识别，其他字段仍可确认；"+reason;
            else errors.add(reason);
        }
        if(!errors.isEmpty())throw new IllegalArgumentException(String.join("\n",errors));
        return next;
    }
    private MappingConfirmation(){}
}
