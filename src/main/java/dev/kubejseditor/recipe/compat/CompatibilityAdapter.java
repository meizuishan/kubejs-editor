package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import dev.kubejseditor.recipe.*;
import java.util.*;

public final class CompatibilityAdapter implements RecipeAdapter {
    public final CompatDefinition definition;private final JsonObject example;
    public CompatibilityAdapter(CompatDefinition definition,JsonObject example){this.definition=definition;this.example=example.deepCopy();definition.check();}
    @Override public String type(){return definition.type;}
    @Override public boolean canCreate(){return false;}
    @Override public String title(){return type()+" · 通用适配";}
    @Override public JsonObject newRecipe(){return example.deepCopy();}
    @Override public RecipePresentation presentation(JsonObject data){return new RecipePresentation(data,CompatDefinitions.current(definition,data));}
    @Override public List<String> validate(JsonObject data){try{return presentation(data).validate();}catch(RuntimeException error){return List.of(error.getMessage());}}
    @Override public RecipeLimits limits(){return new RecipeLimits(256,256,256,256);}
}
