package dev.kubejseditor.recipe;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject.RecipeDraft;

/** Recipe loading metadata is separate from mod-specific fields named "conditions". */
public final class LoadConditions {
    public static final String KEY="neoforge:conditions",PRESERVE="preserve",IGNORE="ignore";
    public static boolean present(JsonObject data){return data!=null&&data.has(KEY);}
    public static boolean ignored(RecipeDraft draft){return IGNORE.equals(draft.loadConditionPolicy);}
    public static void restoreFromSource(JsonObject encoded,JsonObject source){if(present(source))encoded.add(KEY,source.get(KEY).deepCopy());}
    public static JsonObject body(JsonObject data){var out=data.deepCopy();out.remove(KEY);return out;}
    public static JsonObject effective(JsonObject data,RecipeDraft draft){return ignored(draft)?body(data):data;}
    private LoadConditions(){}
}
