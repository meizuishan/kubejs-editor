package dev.kubejseditor.module;

/** Modules add adapters through RecipeAdapters.register and declare config ownership here. */
public interface EditorModule {
    String id();
    String title();
    boolean ownsConfig(String modId);
}
