package dev.kubejseditor.runtime;

import dev.kubejseditor.model.EditorProject.ScriptDraft;
import dev.kubejseditor.storage.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class EditorScripts {
    public record Entry(String path,String phase,String status,boolean managed){}
    public static Path checked(Path root,String relative)throws IOException{
        String path=relative.replace('\\','/');
        if(!path.matches("kubejs/(server_scripts|startup_scripts|client_scripts)/.+\\.js"))throw new IOException("只能编辑三个脚本目录中的 JS 文件");
        if(Arrays.stream(path.split("/",-1)).anyMatch(s->s.isEmpty()||s.equals(".")||s.equals("..")))throw new IOException("脚本路径不能包含空段、. 或 ..");
        Path file=FileTransaction.checkedPath(root,path);Path folder=root.toAbsolutePath().normalize().resolve("kubejs/"+path.split("/")[1]);
        if(!file.startsWith(folder))throw new IOException("脚本路径超出所属目录");return file;
    }
    public static List<Entry> list()throws IOException{
        List<Entry> result=new ArrayList<>();var root=EditorRuntime.root();
        for(String phase:List.of("server_scripts","startup_scripts","client_scripts")){
            Path folder=root.resolve("kubejs/"+phase);if(!Files.exists(folder))continue;
            try(var paths=Files.walk(folder)){for(Path p:paths.filter(f->Files.isRegularFile(f)&&f.toString().endsWith(".js")).sorted().toList()){
                String path=root.relativize(p).toString().replace('\\','/');checked(root,path);
                String status=ScriptIndex.loaded.getOrDefault(path,"尚未确认加载");
                var console=phase.equals("server_scripts")?dev.latvian.mods.kubejs.script.ConsoleJS.SERVER:phase.equals("client_scripts")?dev.latvian.mods.kubejs.script.ConsoleJS.CLIENT:dev.latvian.mods.kubejs.script.ConsoleJS.STARTUP;
                if(ScriptIndex.errors(console).stream().anyMatch(error->error.contains(path.substring("kubejs/".length()).replaceFirst("/",":"))))status="加载或执行报错";
                var activation=EditorRuntime.state().activation;if(activation!=null&&activation.hashes.containsKey(path))status=activation.restart?"等待重启":"等待成功重载";
                if(EditorRuntime.project().scripts.containsKey(path))status="源码草稿";
                result.add(new Entry(path,phase,status,EditorRuntime.state().isManaged(path)));
            }}
        }return result;
    }
    public static ScriptDraft open(String path)throws IOException{
        ScriptDraft existing=EditorRuntime.project().scripts.get(path);if(existing!=null)return WorkspaceState.copy(existing,ScriptDraft.class);
        Path file=checked(EditorRuntime.root(),path);if(Files.size(file)>1024*1024)throw new IOException("文本编辑器支持最多 1 MiB 的脚本");
        ScriptDraft d=new ScriptDraft();d.path=path;d.baseHash=WorkspaceState.hash(file);d.text=Files.readString(file);return d;
    }
    public static void stage(ScriptDraft draft)throws IOException{
        EditorRuntime.requireEditable();
        checked(EditorRuntime.root(),draft.path);
        if(draft.text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024*1024)throw new IOException("脚本超过 1 MiB");
        if(EditorRuntime.state().isManaged(draft.path)&&!draft.takeover)throw new IOException("请先选择转为手写");
        EditorRuntime.project().scripts.put(draft.path,WorkspaceState.copy(draft,ScriptDraft.class));EditorRuntime.saveDraft();
    }
}
