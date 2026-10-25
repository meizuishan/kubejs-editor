package dev.kubejseditor.recipe;

import com.google.gson.JsonObject;
import java.util.List;

/** Extension point: a module owns the layout, import boundary and validation for a serializer. */
public interface RecipeAdapter {
    String type();
    String title();
    JsonObject newRecipe();
    List<String> validate(JsonObject data);
    /** Import/discovery adapters are not authoring templates. */
    default boolean canCreate(){return true;}
    default dev.kubejseditor.recipe.compat.RecipePresentation presentation(JsonObject data){return null;}
    default RecipeLimits limits(){return RecipeLimits.forType(type());}
    default boolean canEdit(JsonObject data) { return validate(data).isEmpty(); }
}
