package dev.kubejseditor.model;

/** Optional schema-2 registration fields; old basic blocks retain their previous defaults. */
public final class BlockOptions {
    public CropOptions crop=new CropOptions();
    public StorageOptions storage=new StorageOptions();
    public String type="basic",sound="stone",render="solid",miningTool="none",miningLevel="none",behaviour="oak";
    public String topTexture="",itemTexture="";
    public boolean collision=true,opaque=true,requiresTool=false,dropSelf=true,unbreakable=false;
    public double slipperiness=.6,speedFactor=1,jumpFactor=1;
    public int pressedTicks=20;
}
