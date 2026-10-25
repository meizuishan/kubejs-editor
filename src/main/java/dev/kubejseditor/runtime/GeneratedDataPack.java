package dev.kubejseditor.runtime;

import dev.kubejseditor.model.RegistryEntryTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native high-priority data overlay, isolated from all hand-authored KubeJS/data files. */
public final class GeneratedDataPack {
    private static final byte[] METADATA="{\"pack\":{\"pack_format\":48,\"description\":\"KubeJS Editor generated registry data\"}}".getBytes(StandardCharsets.UTF_8);
    public static void register(AddPackFindersEvent event){
        if(event.getPackType()!=PackType.SERVER_DATA)return;
        event.addRepositorySource(consumer->{var location=new PackLocationInfo("kubejs_editor:generated_data",Component.literal("KubeJS Editor data"),PackSource.BUILT_IN,Optional.empty());
            var supplier=new Pack.ResourcesSupplier(){
                public PackResources openPrimary(PackLocationInfo info){return new PathPackResources(info,FMLPaths.GAMEDIR.get().resolve(RegistryEntryTarget.PACK)){
                    @Override public IoSupplier<InputStream> getRootResource(String... elements){if(elements.length==1&&elements[0].equals("pack.mcmeta"))return ()->new ByteArrayInputStream(METADATA);return super.getRootResource(elements);}
                };}
                public PackResources openFull(PackLocationInfo info,Pack.Metadata metadata){return openPrimary(info);}
            };
            var pack=Pack.readMetaAndCreate(location,supplier,PackType.SERVER_DATA,new PackSelectionConfig(true,Pack.Position.TOP,true));if(pack!=null)consumer.accept(pack);
        });
    }
    private GeneratedDataPack(){}
}
