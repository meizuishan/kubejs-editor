package dev.kubejseditor.content;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.block.entity.BlockEntityAttachmentRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

public final class EditorContentPlugin implements KubeJSPlugin {
    @Override public void registerBuilderTypes(BuilderTypeRegistry registry){registry.of(Registries.BLOCK,types->types.add(ResourceLocation.parse("kubejs_editor:storage"),StorageBlockBuilder.class,StorageBlockBuilder::new));}
    @Override public void registerBlockEntityAttachments(BlockEntityAttachmentRegistry registry){registry.register(StorageAttachment.TYPE);}
}
