package dev.kubejseditor.client;

import dev.kubejseditor.model.EditorProject.RecipeDraft;
import dev.kubejseditor.runtime.*;
import dev.kubejseditor.recipe.LoadConditions;
import dev.kubejseditor.storage.WorkspaceState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class CompatibilityScreenOpen {
    public static void open(Screen parent,String id){
        try{if(!dev.kubejseditor.recipe.SimpleRecipes.enabled()){dev.kubejseditor.recipe.SimpleRecipes.setEnabled(EditorRuntime.root(),true);EditorRuntime.invalidateRecipes();}}catch(java.io.IOException e){throw new IllegalStateException(e);}
        var found=EditorRuntime.recipe(id);
        var draft=EditorRuntime.project().recipes.get(id);var applied=EditorRuntime.state().applied.recipes.get(id);
        if(draft==null&&applied!=null){
            draft=WorkspaceState.copy(applied,RecipeDraft.class);draft.operation="replace";
            if(found!=null&&found.data()!=null){draft.data=found.data().deepCopy();if(LoadConditions.present(applied.data))draft.data.add(LoadConditions.KEY,applied.data.get(LoadConditions.KEY).deepCopy());}
        }
        if(draft==null){
            if(found==null)throw new IllegalArgumentException("未找到来源或已保存规则："+id);
            if(found.data()==null){Minecraft.getInstance().setScreen(new TextScreen(parent,"配方来源",found.error()));return;}
            draft=new RecipeDraft(id,found.data());draft.operation="replace";draft.origin="existing";draft.source=found.data().deepCopy();draft.registryTarget=found.registryTarget();
        }
        Minecraft.getInstance().setScreen(new CompatibilityScreen(parent,draft));
    }
    private CompatibilityScreenOpen(){}
}
