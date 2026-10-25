package dev.kubejseditor.client;

import dev.kubejseditor.runtime.*;
import java.util.*;

/** Persist exceptional UI failures once; ordinary viewing never overwrites reload diagnostics. */
public final class CompatDiagnostics {
    private static final Set<String> recorded=new HashSet<>();
    public static void failure(String phase,String context,Throwable error){String key=EditorRuntime.root()+"/"+phase+"/"+context+"/"+error;if(!recorded.add(key))return;var d=new EditorDiagnostics.Diagnostic();d.operation="compatibility-ui";d.phase=phase;d.outcome="failure";d.message=context+" · "+error;d.error=EditorDiagnostics.Error.of(error);d.throwable=error;d.notes.add(context);EditorDiagnostics.record(EditorRuntime.root(),d);}
    private static final String FILE="kubejs/config/kubejs_editor/last-compatibility-diagnostic.json";
    public static void unavailable(String context,String reason){String key=EditorRuntime.root()+"/readonly/"+context+"/"+reason;if(!recorded.add(key))return;var d=new EditorDiagnostics.Diagnostic();d.operation="compatibility-ui";d.phase="source";d.outcome="read-only";d.message=reason;d.notes.add(context);try{dev.kubejseditor.storage.FileTransaction.atomicWrite(EditorRuntime.root().resolve(FILE),dev.kubejseditor.storage.ProjectStore.JSON.toJson(d).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){System.err.println("[KubeJS Editor] 无法保存适配说明："+e);}}
    public static String read(){try{return java.nio.file.Files.readString(EditorRuntime.root().resolve(FILE));}catch(Exception e){return "尚无只读适配记录";}}
    private CompatDiagnostics(){}
}
