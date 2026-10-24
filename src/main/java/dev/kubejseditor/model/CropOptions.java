package dev.kubejseditor.model;

import java.util.*;

public final class CropOptions {
    public int stages=8,outputCount=1,bonemealStages=3;
    public String seedName="新作物种子",seedTexture="minecraft:item/wheat_seeds",output="minecraft:wheat";
    public boolean seedDrops=true,vanillaGrowth=true,vanillaBonemeal=true;
    public double growthSpeed=1;
    public List<String> textures=new ArrayList<>();
    public CropOptions(){for(int i=0;i<8;i++)textures.add("minecraft:block/wheat_stage"+i);}
    public void validate(){
        if(stages<2||stages>16||textures==null||textures.size()!=stages)throw new IllegalArgumentException("作物阶段数须为 2～16，并为每个阶段指定材质");
        textures.forEach(EditorProject::requireId);EditorProject.requireId(seedTexture);EditorProject.requireId(output);
        if(seedName==null||seedName.isBlank()||outputCount<1||outputCount>64||bonemealStages<1||bonemealStages>15||!Double.isFinite(growthSpeed)||growthSpeed<0)
            throw new IllegalArgumentException("作物名称不能为空；产物数量 1～64，骨粉增长 1～15 阶段，生长速度须为非负有限数");
    }
}
