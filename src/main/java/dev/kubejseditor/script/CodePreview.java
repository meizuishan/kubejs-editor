package dev.kubejseditor.script;

import dev.kubejseditor.model.EditorProject;
import dev.kubejseditor.storage.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Per-record inspection remains available even when a different record cannot be generated. */
public final class CodePreview {
    public record Page(String key,String title,String content){}
    public static List<Page> pages(EditorProject content,EditorProject pending){
        List<Page> pages=new ArrayList<>();
        content.recipes.forEach((id,r)->{
            var single=new EditorProject();single.recipes.put(id,r);
            pages.add(new Page("recipe:"+id,"配方 · "+id,generate(single,r)));
        });
        content.items.forEach((id,item)->{
            var single=new EditorProject();single.items.put(id,item);
            pages.add(new Page("item:"+id,"物品 · "+id,generate(single,item)));
        });
        pending.scripts.forEach((path,s)->pages.add(new Page("script:"+path,path,s.text)));
        if(!pending.configs.isEmpty())pages.add(new Page("config","模组参数",ProjectStore.JSON.toJson(pending.configs)));
        if(!pending.assets.isEmpty())pages.add(new Page("assets","贴图文件",ProjectStore.JSON.toJson(pending.assets)));
        return List.copyOf(pages);
    }
    private static String generate(EditorProject single,Object raw){
        try{
            StringBuilder text=new StringBuilder("// 单条记录预览；应用时按文件合并。\n");
            KjsGenerator.generate(single).files().forEach((path,bytes)->{
                if(!path.equals(ProjectStore.FILE)&&!path.startsWith("kubejs/config/"))text.append("\n// ").append(path).append('\n').append(new String(bytes,StandardCharsets.UTF_8)).append('\n');
            });return text.toString();
        }catch(Exception e){return "// 尚未通过校验，以下为保留的原始数据，不能直接应用。\n// "+e.getMessage()+"\n\n"+ProjectStore.JSON.toJson(raw);}
    }
    private CodePreview(){}
}
