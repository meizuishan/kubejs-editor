package dev.kubejseditor.script;

import dev.kubejseditor.model.*;

/** Generates only public KubeJS builder calls; native builders own shape/state/interaction/loot. */
public final class BlockRegistration {
    public static void append(StringBuilder out,EditorProject.ItemDraft item){
        var b=BlockKinds.options(item);
        if(b.type.equals("crop")){appendCrop(out,item);return;}
        // Preserve the old basic-block bytes so a recipe-only save after upgrading does not
        // rewrite an otherwise unchanged startup script and demand another restart.
        boolean legacy=b.type.equals("basic")&&b.sound.equals("stone")&&b.render.equals("solid")&&b.collision&&b.opaque&&!b.requiresTool&&b.dropSelf&&!b.unbreakable&&b.miningTool.equals("none")&&b.miningLevel.equals("none")&&b.slipperiness==.6&&b.speedFactor==1&&b.jumpFactor==1;
        out.append("StartupEvents.registry('block', event => {\n  event.create(").append(KjsGenerator.quote(item.id));
        if(!b.type.equals("basic"))out.append(",").append(KjsGenerator.quote(b.type.equals("storage")?"kubejs_editor:storage":b.type));out.append(").texture(").append(KjsGenerator.quote(item.texture)).append(")");
        if(b.type.equals("storage")){var storage=b.storage;out.append(".rows(").append(storage.rows).append(").columns(").append(storage.columns).append(").slotCapacity(").append(storage.slotCapacity).append(")");}
        if(b.type.equals("door"))out.append(".textures({top:").append(KjsGenerator.quote(b.topTexture)).append(",bottom:").append(KjsGenerator.quote(item.texture)).append("})");
        out.append(".hardness(").append(ItemKinds.value(item,"hardness",1.5)).append(").resistance(").append(ItemKinds.value(item,"resistance",6)).append(").lightLevel(").append(ItemKinds.value(item,"lightLevel",0)/15).append(")");
        if(legacy){out.append(".stoneSoundType().requiresTool(false)");appendItem(out,item);return;}
        if(b.sound.equals("none"))out.append(".noSoundType()");else out.append(".soundType(").append(KjsGenerator.quote(b.sound)).append(")");
        out.append(".renderType(").append(KjsGenerator.quote(b.render)).append(").opaque(").append(b.opaque).append(").requiresTool(").append(b.requiresTool).append(")");
        out.append(".slipperiness(").append(b.slipperiness).append(").speedFactor(").append(b.speedFactor).append(").jumpFactor(").append(b.jumpFactor).append(")");
        if(!b.collision)out.append(".noCollision()");if(!b.dropSelf)out.append(".noDrops()");if(b.unbreakable)out.append(".unbreakable()");
        if(!b.miningTool.equals("none"))out.append(".tagBlock(").append(KjsGenerator.quote("minecraft:mineable/"+b.miningTool)).append(")");
        if(!b.miningLevel.equals("none"))out.append(".tagBlock(").append(KjsGenerator.quote("minecraft:needs_"+b.miningLevel+"_tool")).append(")");
        if(java.util.Set.of("door","trapdoor","button","pressure_plate").contains(b.type))out.append(".behaviour(").append(KjsGenerator.quote(b.behaviour)).append(")");
        if(b.type.equals("door")&&b.behaviour.equals("oak"))out.append(".wooden()");
        if(b.type.equals("button")||b.type.equals("pressure_plate"))out.append(".ticksToStayPressed(").append(b.pressedTicks).append(")");
        appendItem(out,item);
    }
    private static void appendCrop(StringBuilder out,EditorProject.ItemDraft item){
        var c=BlockKinds.options(item).crop;
        out.append("StartupEvents.registry('block', event => {\n  event.create(").append(KjsGenerator.quote(item.id)).append(",'crop').age(").append(c.stages-1).append(", shapes => { ");
        for(int i=0;i<c.stages;i++)out.append("shapes.shape(").append(i).append(",0,0,0,16,").append(Math.max(2,16*(i+1)/c.stages)).append(",16);");
        out.append(" }).textures({");
        for(int i=0;i<c.stages;i++){if(i>0)out.append(',');out.append(KjsGenerator.quote(String.valueOf(i))).append(':').append(KjsGenerator.quote(c.textures.get(i)));}
        out.append("}).crop(").append(KjsGenerator.quote(c.output)).append(',').append(c.outputCount).append(')');
        if(!c.seedDrops)out.append(".noSeeds()");
        if(!c.vanillaGrowth)out.append(".growTick(ctx => ").append(c.growthSpeed).append(')');
        if(!c.vanillaBonemeal)out.append(".bonemeal(ctx => ").append(c.bonemealStages).append(')');
        out.append(".item(seed => {seed.texture(").append(KjsGenerator.quote(c.seedTexture)).append(");");
        for(var prop:item.properties.entrySet())if(java.util.Set.of("maxStackSize","burnTime").contains(prop.getKey()))out.append("seed.").append(prop.getKey()).append('(').append(prop.getValue()).append(");");
        out.append("});\n});\n");
    }
    private static void appendItem(StringBuilder out,EditorProject.ItemDraft item){
        out.append(".item(item => {");
        for(var prop:item.properties.entrySet())if(java.util.Set.of("maxStackSize","burnTime").contains(prop.getKey()))out.append("item.").append(prop.getKey()).append('(').append(prop.getValue()).append(");");
        out.append("});\n});\n");
    }
    public static String itemModelPath(EditorProject.ItemDraft item){String[] id=item.id.split(":",2);return "kubejs/assets/"+id[0]+"/models/item/"+id[1]+".json";}
    public static byte[] itemModel(EditorProject.ItemDraft item){
        var model=new com.google.gson.JsonObject();model.addProperty("parent","minecraft:item/generated");var textures=new com.google.gson.JsonObject();textures.addProperty("layer0",BlockKinds.options(item).itemTexture);model.add("textures",textures);return dev.kubejseditor.storage.WorkspaceState.bytes(model);
    }
    private BlockRegistration(){}
}
