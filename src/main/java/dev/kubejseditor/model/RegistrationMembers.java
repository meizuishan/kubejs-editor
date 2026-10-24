package dev.kubejseditor.model;

import java.util.*;
import dev.kubejseditor.model.EditorProject.ItemDraft;

/** The ownership unit is one form, which may register several registry entries. */
public final class RegistrationMembers {
    public record Member(String registry,String id){}
    public static boolean crop(ItemDraft d){return ItemKinds.kind(d).equals("block")&&BlockKinds.options(d).type.equals("crop");}
    public static String itemId(ItemDraft d){return crop(d)?d.id+"_seeds":d.id;}
    public static List<Member> of(ItemDraft d){
        if(!d.register)return List.of();
        var list=new ArrayList<Member>();list.add(new Member("item",itemId(d)));
        if(ItemKinds.kind(d).equals("block")){list.add(new Member("block",d.id));if(BlockKinds.options(d).type.equals("storage"))list.add(new Member("block_entity_type",d.id));}
        return List.copyOf(list);
    }
    public static void sameStructure(ItemDraft previous,ItemDraft next){
        if(previous==null||!previous.register)return;
        if(!ItemKinds.kind(previous).equals(ItemKinds.kind(next)))throw new IllegalArgumentException("已生效注册不能变更类型，请使用新 ID");
        if(!ItemKinds.kind(next).equals("block"))return;
        var a=BlockKinds.options(previous);var b=BlockKinds.options(next);
        if(!a.type.equals(b.type)||a.type.equals("crop")&&a.crop.stages!=b.crop.stages||a.type.equals("storage")&&(a.storage.rows!=b.storage.rows||a.storage.columns!=b.storage.columns||a.storage.slotCapacity!=b.storage.slotCapacity))
            throw new IllegalArgumentException("已生效注册的类型、作物阶段数、容器行列和容量不能原地修改，请使用新 ID");
    }
    private RegistrationMembers(){}
}
